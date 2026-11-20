package com.tms.gateway.modules.audit.service;

import com.tms.gateway.core.dto.PagedResponse;
import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.modules.audit.dto.AuditLogDto;
import com.tms.gateway.modules.audit.entity.AuditLogEntity;
import com.tms.gateway.modules.audit.repository.AuditLogRepository;
import com.tms.gateway.modules.user.entity.PgMerchantUserEntity;
import com.tms.gateway.modules.user.repository.PgMerchantUserRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Persists and queries the merchant audit trail.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_PAGE_SIZE = 100;

    private final AuditLogRepository repository;
    private final PgMerchantUserRepository userRepository;
    private final AuditPayloadSanitizer sanitizer;

    /**
     * Saves an entry off the request thread. A failure here is logged and never
     * reaches the merchant's request.
     *
     * <p>
     * Unauthenticated actions (login, registration, password reset) carry only the
     * email from the request body; it is resolved to the account here so the
     * merchant sees these entries in their own log.
     */
    @Async
    public void record(AuditLogEntity entry) {
        try {
            if (entry.getUserId() == null && entry.getActorEmail() != null) {
                userRepository.findByEmail(entry.getActorEmail()).ifPresent(user -> attachUser(entry, user));
            }
            repository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to write audit log for {} {}: {}", entry.getHttpMethod(), entry.getRequestPath(),
                    e.getMessage());
        }
    }

    /**
     * Paginated audit log for the caller's merchant (or the caller alone when the
     * account has no merchant ID).
     */
    public Map<String, Object> list(GatewayMerchantUserDetails user, String search, String module, String status,
            Instant startDate, Instant endDate, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<AuditLogDto> result = repository
                .findFiltered(merchantScope(user), user.getUserId(), blankToNull(search), blankToNull(module),
                        normaliseStatus(status), startDate, endDate, PageRequest.of(safePage - 1, safeSize))
                .map(this::toDto);

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("search", search);
        filters.put("module", module);
        filters.put("status", status);
        filters.put("start_date", startDate);
        filters.put("end_date", endDate);
        return PagedResponse.from(result, "/audit-logs", Map.of("filters", filters));
    }

    /** A single entry, only if it belongs to the caller's merchant. */
    public Optional<AuditLogDto> show(GatewayMerchantUserDetails user, Long id) {
        String merchantId = merchantScope(user);
        return repository.findById(id)
                .filter(entry -> merchantId != null
                        ? merchantId.equals(entry.getMerchantId())
                        : Objects.equals(user.getUserId(), entry.getUserId()))
                .map(this::toDto);
    }

    AuditLogDto toDto(AuditLogEntity e) {
        ZonedDateTime at = e.getCreatedAt() == null ? null : e.getCreatedAt().atZone(ZoneId.systemDefault());
        return new AuditLogDto(e.getId(), e.getAction(), e.getModule(),
                new AuditLogDto.Actor(e.getUserId(), e.getMerchantId(), e.getActorEmail(), e.getActorName()),
                e.getCreatedAt(), at == null ? null : at.format(DATE), at == null ? null : at.format(TIME),
                e.getStatus(), e.getStatusCode(), e.getResultMessage(), e.getErrorMessage(), e.getHttpMethod(),
                e.getEndpoint(), e.getRequestPath(), e.getResourceId(), sanitizer.fromJson(e.getRequestPayload()),
                sanitizer.fromJson(e.getResponsePayload()), e.getIpAddress(), e.getUserAgent(), e.getDurationMs());
    }

    private static void attachUser(AuditLogEntity entry, PgMerchantUserEntity user) {
        entry.setUserId(user.getId());
        entry.setMerchantId(user.getMerchantId());
        entry.setActorName(displayName(user.getFirstName(), user.getLastName(), user.getBusinessName()));
    }

    /** Full name, falling back to the business name. */
    public static String displayName(String firstName, String lastName, String businessName) {
        String full = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
        return full.isEmpty() ? businessName : full;
    }

    private static String merchantScope(GatewayMerchantUserDetails user) {
        return blankToNull(user.getMerchantId());
    }

    private static String normaliseStatus(String status) {
        String value = blankToNull(status);
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
