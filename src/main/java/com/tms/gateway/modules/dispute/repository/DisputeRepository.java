package com.tms.gateway.modules.dispute.repository;

import com.tms.gateway.modules.dispute.entity.DisputeEntity;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DisputeRepository extends JpaRepository<DisputeEntity, Long> {

    Optional<DisputeEntity> findByIdAndUserId(Long id, Long userId);

    Page<DisputeEntity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Filtered dispute query with optional search, status, and date range.
     */
    @Query(value = """
            SELECT * FROM disputes d
            WHERE d.user_id = :userId
              AND (CAST(:search AS text) IS NULL
                   OR LOWER(d.subject) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%'))
                   OR LOWER(d.transaction_reference) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%'))
                   OR CAST(:search AS text) ~ '^[0-9]+$' AND d.id = CAST(CAST(:search AS text) AS BIGINT))
              AND (CAST(:status AS text) IS NULL OR d.status_code = CAST(:status AS text))
              AND (CAST(:dateFrom AS timestamptz) IS NULL OR d.created_at >= CAST(:dateFrom AS timestamptz))
              AND (CAST(:dateTo AS timestamptz) IS NULL OR d.created_at <= CAST(:dateTo AS timestamptz))
            ORDER BY d.created_at DESC
            """, countQuery = """
            SELECT COUNT(*) FROM disputes d
            WHERE d.user_id = :userId
              AND (CAST(:search AS text) IS NULL
                   OR LOWER(d.subject) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%'))
                   OR LOWER(d.transaction_reference) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%'))
                   OR CAST(:search AS text) ~ '^[0-9]+$' AND d.id = CAST(CAST(:search AS text) AS BIGINT))
              AND (CAST(:status AS text) IS NULL OR d.status_code = CAST(:status AS text))
              AND (CAST(:dateFrom AS timestamptz) IS NULL OR d.created_at >= CAST(:dateFrom AS timestamptz))
              AND (CAST(:dateTo AS timestamptz) IS NULL OR d.created_at <= CAST(:dateTo AS timestamptz))
            """, nativeQuery = true)
    Page<DisputeEntity> findFiltered(@Param("userId") Long userId, @Param("search") String search,
            @Param("status") String status, @Param("dateFrom") Instant dateFrom, @Param("dateTo") Instant dateTo,
            Pageable pageable);
}
