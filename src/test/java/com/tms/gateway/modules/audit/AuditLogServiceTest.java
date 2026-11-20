package com.tms.gateway.modules.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.modules.audit.dto.AuditLogDto;
import com.tms.gateway.modules.audit.entity.AuditLogEntity;
import com.tms.gateway.modules.audit.repository.AuditLogRepository;
import com.tms.gateway.modules.audit.service.AuditLogService;
import com.tms.gateway.modules.audit.service.AuditPayloadSanitizer;
import com.tms.gateway.modules.user.entity.PgMerchantUserEntity;
import com.tms.gateway.modules.user.repository.PgMerchantUserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AuditLogServiceTest {

    private final AuditLogRepository repository = mock(AuditLogRepository.class);
    private final PgMerchantUserRepository users = mock(PgMerchantUserRepository.class);
    private final AuditLogService service = new AuditLogService(repository, users,
            new AuditPayloadSanitizer(JsonMapper.builder().build()));

    private final GatewayMerchantUserDetails merchant = GatewayMerchantUserDetails.builder().id(4L).merchantId("m1")
            .email("owner@example.com").build();

    @Test
    void recordLinksAnonymousActorToAccountByEmail() {
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(PgMerchantUserEntity.builder().id(4L)
                .merchantId("m1").email("owner@example.com").firstName("Ada").lastName("Obi").build()));
        AuditLogEntity entry = AuditLogEntity.builder().actorEmail("owner@example.com").action("Login").build();

        service.record(entry);

        verify(repository).save(entry);
        assertThat(entry.getUserId()).isEqualTo(4L);
        assertThat(entry.getMerchantId()).isEqualTo("m1");
        assertThat(entry.getActorName()).isEqualTo("Ada Obi");
    }

    @Test
    void recordSwallowsPersistenceFailures() {
        when(repository.save(any())).thenThrow(new RuntimeException("db down"));

        service.record(AuditLogEntity.builder().userId(4L).action("Create webhook").build());
    }

    @Test
    void showReturnsOnlyEntriesOfTheCallersMerchant() {
        when(repository.findById(1L)).thenReturn(Optional.of(entry(1L, "m1")));
        when(repository.findById(2L)).thenReturn(Optional.of(entry(2L, "other")));

        Optional<AuditLogDto> own = service.show(merchant, 1L);
        assertThat(own).isPresent();
        assertThat(own.get().date()).matches("\\d{4}-\\d{2}-\\d{2}");
        assertThat(own.get().time()).matches("\\d{2}:\\d{2}:\\d{2}");
        assertThat(own.get().requestPayload()).isInstanceOf(java.util.Map.class);
        assertThat(service.show(merchant, 2L)).isEmpty();
    }

    private static AuditLogEntity entry(Long id, String merchantId) {
        return AuditLogEntity.builder().id(id).merchantId(merchantId).userId(9L).action("Create webhook")
                .requestPayload("{\"url\":\"https://x\"}").createdAt(Instant.parse("2026-11-20T10:15:30Z")).build();
    }
}
