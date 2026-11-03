package com.tms.gateway.modules.transaction.repository;

import com.tms.gateway.modules.transaction.entity.PgMerchantTransactionEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for pg_merchant transaction replicas.
 */
@Repository
public interface PgMerchantTransactionRepository extends JpaRepository<PgMerchantTransactionEntity, Long> {

    Optional<PgMerchantTransactionEntity> findByReference(String reference);

    Optional<PgMerchantTransactionEntity> findByUserIdAndReference(Long userId, String reference);

    Page<PgMerchantTransactionEntity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Search and filter transactions with optional parameters. All filter
     * parameters are optional (null = no filter).
     */
    @Query("""
            SELECT t FROM PgMerchantTransactionEntity t
            WHERE t.userId = :userId
            AND (:statusCode IS NULL OR t.statusCode = :statusCode)
            AND (:paymentMethod IS NULL OR t.paymentMethod = :paymentMethod)
            AND (:startDate IS NULL OR t.createdAt >= :startDate)
            AND (:endDate IS NULL OR t.createdAt <= :endDate)
            AND (:search IS NULL OR (
                LOWER(t.reference) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(t.customerEmail) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(t.customerName) LIKE LOWER(CONCAT('%', :search, '%'))
            ))
            ORDER BY t.createdAt DESC
            """)
    Page<PgMerchantTransactionEntity> findByFilters(@Param("userId") Long userId,
            @Param("statusCode") String statusCode, @Param("paymentMethod") String paymentMethod,
            @Param("startDate") Instant startDate, @Param("endDate") Instant endDate, @Param("search") String search,
            Pageable pageable);

    /**
     * Get transaction counts by status for a user.
     */
    @Query("""
            SELECT t.statusCode, COUNT(t), COALESCE(SUM(t.amount), 0)
            FROM PgMerchantTransactionEntity t
            WHERE t.userId = :userId
            GROUP BY t.statusCode
            """)
    List<Object[]> getSummaryByStatus(@Param("userId") Long userId);

    /**
     * Get total transaction count and amount for a user.
     */
    @Query("""
            SELECT COUNT(t), COALESCE(SUM(t.amount), 0)
            FROM PgMerchantTransactionEntity t
            WHERE t.userId = :userId
            """)
    Object[] getTotalSummary(@Param("userId") Long userId);

    boolean existsByReference(String reference);
}
