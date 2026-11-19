package com.tms.gateway.modules.dispute.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Replicated dispute entity from tms-dispute.
 */
@Entity
@Table(name = "disputes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeEntity {

    @Id
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "transaction_reference")
    private String transactionReference;

    @Column(nullable = false)
    private String subject;

    @Column(columnDefinition = "text")
    private String message;

    @Column(name = "status_code", nullable = false)
    private String statusCode;

    @Column(name = "status_description")
    private String statusDescription;

    @Column(name = "attachment_url")
    private String attachmentUrl;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
