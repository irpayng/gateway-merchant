package com.tms.gateway.modules.compliance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Replica of PG merchant profile (step 1) from tms-kyc.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "pg_merchant_profiles", indexes = {@Index(name = "idx_profile_user", columnList = "user_id")})
public class PgMerchantProfileReplicaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id")
    private Long sourceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compliance_id", nullable = false)
    private PgMerchantComplianceReplicaEntity compliance;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 3)
    private String country;

    @Column(length = 100)
    private String industry;

    @Column(length = 100)
    private String category;

    @Column(name = "trading_name", length = 255)
    private String tradingName;

    @Column(name = "sales_volume", length = 50)
    private String salesVolume;

    @Column(name = "business_type", length = 30)
    private String businessType;

    @Column(name = "business_name", length = 255)
    private String businessName;

    @Column(name = "rc_number", length = 100)
    private String rcNumber;

    @Column(name = "cac_url", length = 500)
    private String cacUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;
}
