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
 * Replica of PG merchant owner (step 3) from tms-kyc.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "pg_merchant_owners", indexes = {@Index(name = "idx_owner_compliance", columnList = "compliance_id"),
        @Index(name = "idx_owner_user", columnList = "user_id")})
public class PgMerchantOwnerReplicaEntity {

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

    @Column(length = 50)
    private String role;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(length = 255)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 20)
    private String bvn;

    @Column(length = 20)
    private String nin;

    @Column(name = "date_of_birth", length = 10)
    private String dateOfBirth;

    @Column(length = 500)
    private String address;

    @Column(name = "ownership_percentage")
    private Integer ownershipPercentage;

    @Column(name = "id_document_url", length = 500)
    private String idDocumentUrl;

    @Column(name = "id_document_type", length = 30)
    private String idDocumentType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;
}
