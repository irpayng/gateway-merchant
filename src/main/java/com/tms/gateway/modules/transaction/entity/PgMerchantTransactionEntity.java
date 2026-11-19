package com.tms.gateway.modules.transaction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Local replica of a transaction from tms-transaction.
 *
 * <p>
 * This entity is populated by Kafka events from tms-transaction
 * (transaction-result topic) and provides fast local reads for the merchant
 * dashboard. The source of truth is always tms-transaction.
 */
@Entity
@Table(name = "pg_merchant_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PgMerchantTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(nullable = false, unique = true, length = 64)
    private String reference;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_code", length = 50)
    private String productCode;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "service_fee", precision = 19, scale = 2)
    private BigDecimal serviceFee;

    @Column(name = "status_code", nullable = false, length = 30)
    private String statusCode;

    @Column(name = "status_message", length = 500)
    private String statusMessage;

    @Column(length = 50)
    private String channel;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "account_number", length = 20)
    private String accountNumber;

    @Column(name = "provider_code", length = 50)
    private String providerCode;

    @Column(name = "provider_reference", length = 100)
    private String providerReference;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;
}
