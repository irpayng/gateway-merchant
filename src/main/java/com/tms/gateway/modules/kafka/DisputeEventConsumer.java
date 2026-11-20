package com.tms.gateway.modules.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tms.gateway.modules.dispute.entity.ConversationEntity;
import com.tms.gateway.modules.dispute.entity.DisputeEntity;
import com.tms.gateway.modules.dispute.repository.ConversationRepository;
import com.tms.gateway.modules.dispute.repository.DisputeRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka consumer for dispute events from tms-dispute.
 *
 * <p>
 * Replicates disputes and conversations locally for fast read queries. The
 * source of truth is tms-dispute; this is a read-only replica.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DisputeEventConsumer {

    private final ObjectMapper objectMapper;
    private final DisputeRepository disputeRepository;
    private final ConversationRepository conversationRepository;

    /**
     * Listen for dispute lifecycle events. tms-dispute publishes them directly to
     * {@code dispute-events}; {@code outbox.event.dispute} is the Debezium outbox
     * route, kept so a future connector works without code changes. Upserts are
     * idempotent, so receiving an event on both topics is harmless.
     */
    @KafkaListener(topics = {"dispute-events", "outbox.event.dispute"}, groupId = "gateway-merchant-dispute")
    public void consume(ConsumerRecord<String, String> record) {
        String message = record.value();
        try {
            JsonNode event = objectMapper.readTree(message);
            // The Debezium EventRouter emits only the expanded outbox payload as the
            // record value and carries the outbox `type` column in the `eventType`
            // header (transforms.outbox.table.fields.additional.placement =
            // type:header:eventType). Fall back to body fields for other producers.
            String eventType = headerValue(record, "eventType");
            if (eventType == null || eventType.isBlank()) {
                eventType = event.path("eventType").asText(event.path("event_type").asText());
            }
            JsonNode payload = event.has("payload") && event.get("payload").isObject() ? event.get("payload") : event;

            log.debug("Processing dispute event: type={}", eventType);

            switch (eventType) {
                case "dispute_created", "dispute-created" -> upsertDispute(payload);
                case "dispute_updated", "dispute-updated" -> upsertDispute(payload);
                case "dispute_closed", "dispute-closed" -> updateDisputeStatus(payload, "closed");
                case "dispute_resolved", "dispute-resolved" -> updateDisputeStatus(payload, "resolved");
                case "conversation_added", "conversation-added" -> upsertConversation(payload);
                default -> log.warn("Ignoring unknown dispute event type: '{}'", eventType);
            }
        } catch (Exception e) {
            log.error("Failed to process dispute event: {}", message, e);
        }
    }

    /**
     * Alternative topic name for dispute-conversation events (live messages).
     */
    @KafkaListener(topics = "dispute-conversation", groupId = "gateway-merchant-dispute-conv")
    public void consumeConversation(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            upsertConversation(event);
        } catch (Exception e) {
            log.error("Failed to process dispute-conversation event: {}", message, e);
        }
    }

    @Transactional
    protected void upsertDispute(JsonNode data) {
        Long id = data.path("id").asLong(0);
        if (id == 0) {
            log.warn("Dispute event missing id field");
            return;
        }

        DisputeEntity dispute = disputeRepository.findById(id).orElse(new DisputeEntity());
        dispute.setId(id);

        if (data.has("userId") || data.has("user_id")) {
            dispute.setUserId(data.path("userId").asLong(data.path("user_id").asLong()));
        }
        if (data.has("transactionReference") || data.has("transaction_reference")) {
            dispute.setTransactionReference(text(data, "transactionReference", "transaction_reference"));
        }
        if (data.has("subject")) {
            dispute.setSubject(data.path("subject").asText());
        }
        if (data.has("message")) {
            dispute.setMessage(text(data, "message"));
        }
        if (data.has("statusCode") || data.has("status_code")) {
            dispute.setStatusCode(text(data, "statusCode", "status_code"));
        }
        if (data.has("statusDescription") || data.has("status_description")) {
            dispute.setStatusDescription(text(data, "statusDescription", "status_description"));
        }
        if (data.has("attachmentUrl") || data.has("attachment_url")) {
            dispute.setAttachmentUrl(text(data, "attachmentUrl", "attachment_url"));
        }
        if (data.has("resolvedAt") || data.has("resolved_at")) {
            dispute.setResolvedAt(parseInstant(text(data, "resolvedAt", "resolved_at")));
        }
        if (data.has("createdAt") || data.has("created_at")) {
            dispute.setCreatedAt(parseInstant(text(data, "createdAt", "created_at")));
        }
        if (data.has("updatedAt") || data.has("updated_at")) {
            dispute.setUpdatedAt(parseInstant(text(data, "updatedAt", "updated_at")));
        } else {
            dispute.setUpdatedAt(Instant.now());
        }

        disputeRepository.save(dispute);
        log.debug("Upserted dispute: id={}, userId={}, status={}", dispute.getId(), dispute.getUserId(),
                dispute.getStatusCode());
    }

    @Transactional
    protected void updateDisputeStatus(JsonNode data, String status) {
        Long id = data.path("id").asLong(data.path("disputeId").asLong(data.path("dispute_id").asLong(0)));
        if (id == 0) {
            log.warn("Dispute status event missing id field");
            return;
        }

        disputeRepository.findById(id).ifPresent(dispute -> {
            dispute.setStatusCode(status);
            dispute.setUpdatedAt(Instant.now());
            if ("resolved".equals(status)) {
                dispute.setResolvedAt(Instant.now());
            }
            disputeRepository.save(dispute);
            log.debug("Updated dispute status: id={}, status={}", id, status);
        });
    }

    @Transactional
    protected void upsertConversation(JsonNode data) {
        Long id = data.path("id").asLong(0);
        if (id == 0) {
            log.warn("Conversation event missing id field");
            return;
        }

        Long disputeId = data.path("disputeId").asLong(data.path("dispute_id").asLong(0));
        if (disputeId == 0) {
            log.warn("Conversation event missing disputeId field, skipping id={}", id);
            return;
        }

        // Store conversations even when the parent dispute hasn't been replicated
        // yet: they travel on a separate topic, and the opening message of a new
        // dispute is sent before the dispute event itself. The FK was dropped (V9)
        // for this reason, and every read query joins disputes, so an early
        // conversation stays invisible until its dispute arrives.

        ConversationEntity conv = conversationRepository.findById(id).orElse(new ConversationEntity());
        conv.setId(id);

        conv.setDisputeId(disputeId);
        if (data.has("userId") || data.has("user_id")) {
            Long userId = data.path("userId").asLong(data.path("user_id").asLong(0));
            conv.setUserId(userId > 0 ? userId : null);
        }
        if (data.has("senderType") || data.has("sender_type")) {
            conv.setSenderType(text(data, "senderType", "sender_type"));
        }
        if (data.has("senderName") || data.has("sender_name")) {
            conv.setSenderName(text(data, "senderName", "sender_name"));
        }
        if (data.has("message")) {
            conv.setMessage(data.path("message").asText(""));
        }

        // Attachment fields
        if (data.has("attachmentKey") || data.has("attachment_key")) {
            conv.setAttachmentKey(text(data, "attachmentKey", "attachment_key"));
        }
        if (data.has("attachmentName") || data.has("attachment_name")) {
            conv.setAttachmentName(text(data, "attachmentName", "attachment_name"));
        }
        if (data.has("attachmentType") || data.has("attachment_type")) {
            conv.setAttachmentType(text(data, "attachmentType", "attachment_type"));
        }
        if (data.has("attachmentSize") || data.has("attachment_size")) {
            long size = data.path("attachmentSize").asLong(data.path("attachment_size").asLong(0));
            conv.setAttachmentSize(size > 0 ? size : null);
        }

        if (data.has("createdAt") || data.has("created_at")) {
            conv.setCreatedAt(parseInstant(text(data, "createdAt", "created_at")));
        } else if (conv.getCreatedAt() == null) {
            conv.setCreatedAt(Instant.now());
        }

        conversationRepository.save(conv);
        log.debug("Upserted conversation: id={}, disputeId={}, sender={}", conv.getId(), conv.getDisputeId(),
                conv.getSenderType());
    }

    private static String headerValue(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        if (header == null || header.value() == null) {
            return null;
        }
        String value = new String(header.value(), StandardCharsets.UTF_8).trim();
        // JsonConverter-encoded headers arrive quoted, e.g. "\"dispute_created\""
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String text(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.hasNonNull(field)) {
                String value = node.get(field).asText();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            // Handle various formats
            if (value.endsWith("Z")) {
                return Instant.parse(value);
            }
            // Try ISO format with timezone
            return Instant.parse(value.replace(" ", "T") + "Z");
        } catch (Exception e) {
            return null;
        }
    }
}
