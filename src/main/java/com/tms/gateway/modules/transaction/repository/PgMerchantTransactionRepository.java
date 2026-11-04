package com.tms.gateway.modules.transaction.repository;

import com.tms.gateway.modules.transaction.entity.PgMerchantTransactionEntity;
import java.math.BigDecimal;
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

    @Query(value = """
            SELECT LOWER(BTRIM(t.customer_email)) AS "normalizedEmail",
                   MIN(t.created_at) AS "earliestTransactionAt",
                   COALESCE(SUM(CASE WHEN LOWER(t.status_code) IN ('completed', 'successful')
                                     THEN t.amount ELSE 0 END), 0) AS "successfulSpend",
                   COUNT(CASE WHEN LOWER(t.status_code) IN ('completed', 'successful') THEN 1 END) AS "successfulCount",
                   COUNT(*) AS "totalCount",
                   MAX(NULLIF(BTRIM(t.customer_name), '')) AS "transactionName"
            FROM pg_merchant_transactions t
            WHERE t.user_id IN (:userIds)
              AND t.customer_email IS NOT NULL
              AND BTRIM(t.customer_email) <> ''
            GROUP BY LOWER(BTRIM(t.customer_email))
            """, nativeQuery = true)
    List<CustomerTransactionAggregate> aggregateCustomers(@Param("userIds") List<Long> userIds);

    @Query(value = """
            SELECT t.* FROM pg_merchant_transactions t
            WHERE t.user_id IN (:userIds)
              AND t.customer_email IS NOT NULL
              AND LOWER(BTRIM(t.customer_email)) = :normalizedEmail
            ORDER BY t.created_at DESC, t.reference DESC
            """, countQuery = """
            SELECT COUNT(*) FROM pg_merchant_transactions t
            WHERE t.user_id IN (:userIds)
              AND t.customer_email IS NOT NULL
              AND LOWER(BTRIM(t.customer_email)) = :normalizedEmail
            """, nativeQuery = true)
    Page<PgMerchantTransactionEntity> findCustomerTransactions(@Param("userIds") List<Long> userIds,
            @Param("normalizedEmail") String normalizedEmail, Pageable pageable);

    interface CustomerTransactionAggregate {
        String getNormalizedEmail();
        Instant getEarliestTransactionAt();
        BigDecimal getSuccessfulSpend();
        long getSuccessfulCount();
        long getTotalCount();
        String getTransactionName();
    }

    Optional<PgMerchantTransactionEntity> findByReference(String reference);

    Optional<PgMerchantTransactionEntity> findByUserIdAndReference(Long userId, String reference);

    Page<PgMerchantTransactionEntity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Search and filter transactions with optional parameters. All filter
     * parameters are optional (null = no filter).
     * 
     * Uses native SQL to properly handle null parameters in PostgreSQL, avoiding
     * Hibernate's bytea type inference issues with LOWER().
     */
    @Query(value = """
            SELECT * FROM pg_merchant_transactions t
            WHERE t.user_id = :userId
            AND (CAST(:statusCode AS VARCHAR) IS NULL OR t.status_code = :statusCode)
            AND (CAST(:paymentMethod AS VARCHAR) IS NULL OR t.payment_method = :paymentMethod)
            AND (CAST(:startDate AS TIMESTAMP) IS NULL OR t.created_at >= :startDate)
            AND (CAST(:endDate AS TIMESTAMP) IS NULL OR t.created_at <= :endDate)
            AND (COALESCE(CAST(:search AS VARCHAR), '') = '' OR (
                LOWER(t.reference) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%'))
                OR LOWER(COALESCE(t.customer_email, '')) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%'))
                OR LOWER(COALESCE(t.customer_name, '')) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%'))
            ))
            ORDER BY t.created_at DESC
            """, countQuery = """
            SELECT COUNT(*) FROM pg_merchant_transactions t
            WHERE t.user_id = :userId
            AND (CAST(:statusCode AS VARCHAR) IS NULL OR t.status_code = :statusCode)
            AND (CAST(:paymentMethod AS VARCHAR) IS NULL OR t.payment_method = :paymentMethod)
            AND (CAST(:startDate AS TIMESTAMP) IS NULL OR t.created_at >= :startDate)
            AND (CAST(:endDate AS TIMESTAMP) IS NULL OR t.created_at <= :endDate)
            AND (COALESCE(CAST(:search AS VARCHAR), '') = '' OR (
                LOWER(t.reference) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%'))
                OR LOWER(COALESCE(t.customer_email, '')) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%'))
                OR LOWER(COALESCE(t.customer_name, '')) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%'))
            ))
            """, nativeQuery = true)
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
