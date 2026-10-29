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
 * Replica of PG merchant contact (step 2) from tms-kyc.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "pg_merchant_contacts", indexes = {@Index(name = "idx_contact_user", columnList = "user_id")})
public class PgMerchantContactReplicaEntity {

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

    @Column(length = 255)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 500)
    private String address;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(length = 3)
    private String country;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    @Column(name = "support_email", columnDefinition = "text")
    private String supportEmail;

    @Column(name = "dispute_email", columnDefinition = "text")
    private String disputeEmail;

    @Column(name = "lga", columnDefinition = "text")
    private String lga;

    @Column(name = "same_as_support_email")
    private Boolean sameAsSupportEmail;

    @Column(name = "business_address_document_id", columnDefinition = "text")
    private String businessAddressDocumentId;

    @Column(name = "online_links_json", columnDefinition = "text")
    private String onlineLinksJson;
}
