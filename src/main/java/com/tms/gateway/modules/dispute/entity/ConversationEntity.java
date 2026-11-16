package com.tms.gateway.modules.dispute.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Replicated conversation entity from tms-dispute.
 */
@Entity
@Table(name = "conversations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationEntity {

    @Id
    private Long id;

    @Column(name = "dispute_id", nullable = false)
    private Long disputeId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "sender_type", nullable = false)
    private String senderType;

    @Column(name = "sender_name")
    private String senderName;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "attachment_key")
    private String attachmentKey;

    @Column(name = "attachment_name")
    private String attachmentName;

    @Column(name = "attachment_type")
    private String attachmentType;

    @Column(name = "attachment_size")
    private Long attachmentSize;

    @Column(name = "created_at")
    private Instant createdAt;
}
