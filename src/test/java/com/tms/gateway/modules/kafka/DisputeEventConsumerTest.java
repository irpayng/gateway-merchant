package com.tms.gateway.modules.kafka;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tms.gateway.modules.dispute.entity.ConversationEntity;
import com.tms.gateway.modules.dispute.entity.DisputeEntity;
import com.tms.gateway.modules.dispute.repository.ConversationRepository;
import com.tms.gateway.modules.dispute.repository.DisputeRepository;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DisputeEventConsumerTest {

    private final DisputeRepository disputeRepo = mock(DisputeRepository.class);
    private final ConversationRepository convRepo = mock(ConversationRepository.class);
    private final DisputeEventConsumer consumer = new DisputeEventConsumer(new ObjectMapper(), disputeRepo, convRepo);

    // Shape emitted by the Debezium EventRouter: value = expanded outbox payload,
    // event type in the `eventType` header.
    private static final String BODY = "{\"id\":42,\"user_id\":7,\"transaction_reference\":\"TX1\","
            + "\"subject\":\"Failed debit\",\"message\":\"help\",\"status_code\":\"open\","
            + "\"created_at\":\"2026-11-20T10:00:00.123Z\",\"updated_at\":\"2026-11-20T10:00:00.123Z\"}";

    private static ConsumerRecord<String, String> record(String eventTypeHeader) {
        var record = new ConsumerRecord<>("outbox.event.dispute", 0, 0L, "42", BODY);
        if (eventTypeHeader != null) {
            record.headers().add("eventType", eventTypeHeader.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    @Test
    void createdEventFromHeaderIsReplicated() {
        when(disputeRepo.findById(42L)).thenReturn(Optional.empty());

        consumer.consume(record("dispute_created"));

        ArgumentCaptor<DisputeEntity> saved = ArgumentCaptor.forClass(DisputeEntity.class);
        verify(disputeRepo).save(saved.capture());
        assertEquals(42L, saved.getValue().getId());
        assertEquals(7L, saved.getValue().getUserId());
        assertEquals("open", saved.getValue().getStatusCode());
        assertNotNull(saved.getValue().getCreatedAt());
    }

    @Test
    void jsonQuotedHeaderIsAccepted() {
        when(disputeRepo.findById(42L)).thenReturn(Optional.empty());

        consumer.consume(record("\"dispute_updated\""));

        verify(disputeRepo).save(any(DisputeEntity.class));
    }

    @Test
    void directKafkaEnvelopeIsReplicated() {
        when(disputeRepo.findById(42L)).thenReturn(Optional.empty());
        var record = new ConsumerRecord<>("dispute-events", 0, 0L, "42",
                "{\"eventType\":\"dispute_created\",\"payload\":" + BODY + "}");

        consumer.consume(record);

        ArgumentCaptor<DisputeEntity> saved = ArgumentCaptor.forClass(DisputeEntity.class);
        verify(disputeRepo).save(saved.capture());
        assertEquals(42L, saved.getValue().getId());
        assertEquals("Failed debit", saved.getValue().getSubject());
    }

    @Test
    void conversationBeforeItsDisputeIsStored() {
        when(convRepo.findById(5L)).thenReturn(Optional.empty());

        consumer.consumeConversation("{\"id\":5,\"disputeId\":42,\"senderType\":\"user\",\"message\":\"hi\"}");

        verify(convRepo).save(any(ConversationEntity.class));
    }

    @Test
    void missingEventTypeIsIgnored() {
        consumer.consume(record(null));

        verify(disputeRepo, never()).save(any());
    }
}
