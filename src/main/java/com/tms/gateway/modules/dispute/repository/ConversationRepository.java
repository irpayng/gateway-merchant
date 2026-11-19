package com.tms.gateway.modules.dispute.repository;

import com.tms.gateway.modules.dispute.entity.ConversationEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<ConversationEntity, Long> {

    List<ConversationEntity> findByDisputeIdOrderByCreatedAtAsc(Long disputeId);

    /**
     * Count unread admin/agent messages for a specific dispute since last read.
     */
    @Query("""
            SELECT COUNT(c) FROM ConversationEntity c
            WHERE c.disputeId = :disputeId
              AND c.senderType IN ('admin', 'agent')
              AND c.createdAt > :lastReadAt
            """)
    long countUnreadForDispute(@Param("disputeId") Long disputeId, @Param("lastReadAt") Instant lastReadAt);

    /**
     * Count total unread admin/agent messages across all disputes for a user.
     */
    @Query(value = """
            SELECT COUNT(*) FROM conversations c
            JOIN disputes d ON d.id = c.dispute_id
            LEFT JOIN dispute_reads dr ON dr.dispute_id = d.id AND dr.user_id = :userId
            WHERE d.user_id = :userId
              AND c.sender_type IN ('admin', 'agent')
              AND c.created_at > COALESCE(dr.last_read_at, TIMESTAMP '1970-01-01 00:00:00')
            """, nativeQuery = true)
    long countTotalUnread(@Param("userId") Long userId);
}
