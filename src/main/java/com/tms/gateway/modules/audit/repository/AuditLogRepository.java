package com.tms.gateway.modules.audit.repository;

import com.tms.gateway.modules.audit.entity.AuditLogEntity;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

    /**
     * Audit entries owned by a merchant, or by a single user when the account has
     * no merchant ID yet. All filters are optional.
     */
    @Query(value = """
            SELECT * FROM audit_logs a
            WHERE ((CAST(:merchantId AS text) IS NOT NULL AND a.merchant_id = CAST(:merchantId AS text))
                   OR (CAST(:merchantId AS text) IS NULL AND a.user_id = :userId))
              AND (CAST(:search AS text) IS NULL
                   OR LOWER(a.action) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%'))
                   OR LOWER(a.request_path) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%'))
                   OR LOWER(a.actor_email) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%'))
                   OR LOWER(a.resource_id) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')))
              AND (CAST(:module AS text) IS NULL OR a.module = CAST(:module AS text))
              AND (CAST(:status AS text) IS NULL OR a.status = CAST(:status AS text))
              AND (CAST(:dateFrom AS timestamptz) IS NULL OR a.created_at >= CAST(:dateFrom AS timestamptz))
              AND (CAST(:dateTo AS timestamptz) IS NULL OR a.created_at <= CAST(:dateTo AS timestamptz))
            ORDER BY a.created_at DESC, a.id DESC
            """, countQuery = """
            SELECT COUNT(*) FROM audit_logs a
            WHERE ((CAST(:merchantId AS text) IS NOT NULL AND a.merchant_id = CAST(:merchantId AS text))
                   OR (CAST(:merchantId AS text) IS NULL AND a.user_id = :userId))
              AND (CAST(:search AS text) IS NULL
                   OR LOWER(a.action) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%'))
                   OR LOWER(a.request_path) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%'))
                   OR LOWER(a.actor_email) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%'))
                   OR LOWER(a.resource_id) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')))
              AND (CAST(:module AS text) IS NULL OR a.module = CAST(:module AS text))
              AND (CAST(:status AS text) IS NULL OR a.status = CAST(:status AS text))
              AND (CAST(:dateFrom AS timestamptz) IS NULL OR a.created_at >= CAST(:dateFrom AS timestamptz))
              AND (CAST(:dateTo AS timestamptz) IS NULL OR a.created_at <= CAST(:dateTo AS timestamptz))
            """, nativeQuery = true)
    Page<AuditLogEntity> findFiltered(@Param("merchantId") String merchantId, @Param("userId") Long userId,
            @Param("search") String search, @Param("module") String module, @Param("status") String status,
            @Param("dateFrom") Instant dateFrom, @Param("dateTo") Instant dateTo, Pageable pageable);
}
