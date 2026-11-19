package com.tms.gateway.modules.customer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pg_merchant_customers", uniqueConstraints = @UniqueConstraint(name = "uq_pg_merchant_customers_email", columnNames = {
        "merchant_id", "normalized_email"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PgMerchantCustomerEntity {

    @Id
    @Column(name = "customer_ref", length = 36, nullable = false)
    private String customerRef;

    @Column(name = "customer_id", length = 36)
    private String customerId;

    @Column(name = "merchant_id", length = 50, nullable = false)
    private String merchantId;

    @Column(name = "normalized_email", length = 254, nullable = false)
    private String normalizedEmail;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "phone_number", length = 16)
    private String phoneNumber;

    @Column(name = "blacklisted", nullable = false)
    private boolean blacklisted;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
