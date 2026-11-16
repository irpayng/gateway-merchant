package com.tms.gateway.modules.dispute.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tracks when a user last read a dispute thread for unread count calculations.
 */
@Entity
@Table(name = "dispute_reads", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "dispute_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeReadEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "dispute_id", nullable = false)
    private Long disputeId;

    @Column(name = "last_read_at", nullable = false)
    private Instant lastReadAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (lastReadAt == null) {
            lastReadAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
