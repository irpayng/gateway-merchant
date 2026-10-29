package com.tms.gateway.modules.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Local replica of a pg_merchant user from tms-user.
 *
 * <p>
 * This entity is populated by Kafka events from tms-user and provides fast
 * local reads. The source of truth is always tms-user.
 *
 * <p>
 * The id is NOT auto-generated â€” it matches the user.id from tms-user.
 */
@Entity
@Table(name = "pg_merchant_users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PgMerchantUserEntity {

    @Id
    private Long id;

    @Column(name = "merchant_id", nullable = false, length = 50)
    private String merchantId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "business_name")
    private String businessName;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "blocked_at")
    private Instant blockedAt;

    @Column(name = "blocked_reason", length = 500)
    private String blockedReason;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "suspended_reason", length = 500)
    private String suspendedReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    /**
     * Returns true if the user account is active (email verified, not blocked, not
     * suspended).
     */
    public boolean isActive() {
        return emailVerifiedAt != null && blockedAt == null && suspendedAt == null;
    }

    /**
     * Returns the display name (first + last name, or business name as fallback).
     */
    public String getDisplayName() {
        if (firstName != null && !firstName.isBlank()) {
            String name = firstName;
            if (lastName != null && !lastName.isBlank()) {
                name += " " + lastName;
            }
            return name;
        }
        return businessName != null ? businessName : email;
    }
}
