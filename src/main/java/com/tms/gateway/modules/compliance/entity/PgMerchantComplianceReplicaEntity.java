package com.tms.gateway.modules.compliance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Replica of PG merchant compliance from tms-kyc. Populated via Kafka events
 * for fast local reads.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "pg_merchant_compliance", indexes = {@Index(name = "idx_compliance_status", columnList = "status")})
public class PgMerchantComplianceReplicaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id", nullable = false, unique = true)
    private Long sourceId;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "merchant_id", nullable = false, unique = true, length = 50)
    private String merchantId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "current_step", nullable = false)
    private int currentStep;

    @Column(name = "profile_complete", nullable = false)
    private boolean profileComplete;

    @Column(name = "contact_complete", nullable = false)
    private boolean contactComplete;

    @Column(name = "owners_complete", nullable = false)
    private boolean ownersComplete;

    @Column(name = "bank_accounts_complete", nullable = false)
    private boolean bankAccountsComplete;

    @Column(name = "agreement_complete", nullable = false)
    private boolean agreementComplete;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_by", length = 255)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    public boolean isComplete() {
        return profileComplete && contactComplete && ownersComplete && bankAccountsComplete && agreementComplete;
    }

    public boolean isApproved() {
        return "approved".equals(status);
    }
}
