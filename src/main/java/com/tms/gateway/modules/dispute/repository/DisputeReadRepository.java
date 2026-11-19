package com.tms.gateway.modules.dispute.repository;

import com.tms.gateway.modules.dispute.entity.DisputeReadEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DisputeReadRepository extends JpaRepository<DisputeReadEntity, Long> {

    Optional<DisputeReadEntity> findByUserIdAndDisputeId(Long userId, Long disputeId);

    /**
     * Upsert: insert or update last_read_at to now.
     */
    @Modifying
    @Query(value = """
            INSERT INTO dispute_reads (user_id, dispute_id, last_read_at, created_at, updated_at)
            VALUES (:userId, :disputeId, NOW(), NOW(), NOW())
            ON CONFLICT (user_id, dispute_id)
            DO UPDATE SET last_read_at = NOW(), updated_at = NOW()
            """, nativeQuery = true)
    void upsertReadMarker(@Param("userId") Long userId, @Param("disputeId") Long disputeId);
}
