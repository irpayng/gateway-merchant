package com.tms.gateway.modules.kafka;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tms.gateway.modules.compliance.entity.PgMerchantComplianceReplicaEntity;
import com.tms.gateway.modules.compliance.repository.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class ComplianceEventConsumerTest {
    private final PgMerchantComplianceReplicaRepository repo = mock(PgMerchantComplianceReplicaRepository.class);
    private final ComplianceEventConsumer consumer = new ComplianceEventConsumer(new ObjectMapper(), repo,
            mock(PgMerchantProfileReplicaRepository.class), mock(PgMerchantContactReplicaRepository.class),
            mock(PgMerchantOwnerReplicaRepository.class), mock(PgMerchantBankAccountReplicaRepository.class),
            mock(PgMerchantAgreementReplicaRepository.class));
    private String event(long version) throws Exception {
        return new ObjectMapper().writeValueAsString(Map.of("type", "pg_merchant.compliance.updated", "payload",
                "{\"compliance_id\":10,\"user_id\":1,\"merchant_id\":\"m1\",\"status\":\"pending\",\"version\":"
                        + version + "}"));
    }
    @Test
    void olderAndDuplicateEventsDoNotRegressReplica() throws Exception {
        var entity = PgMerchantComplianceReplicaEntity.builder().id(1L).sourceId(10L).userId(1L).merchantId("m1")
                .version(7).status("approved").build();
        when(repo.findBySourceId(10L)).thenReturn(Optional.of(entity));
        consumer.consumeKycEvent(event(6));
        consumer.consumeKycEvent(event(7));
        assertEquals("approved", entity.getStatus());
        verify(repo, times(2)).findBySourceId(10L);
        verify(repo, never()).save(any());
    }
    @Test
    void processingFailurePropagatesForRetryAndRollback() throws Exception {
        when(repo.findBySourceId(10L)).thenThrow(new IllegalStateException("database unavailable"));
        assertThrows(IllegalStateException.class, () -> consumer.consumeKycEvent(event(8)));
    }
}
