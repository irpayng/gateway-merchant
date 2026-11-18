-- ============================================================================
-- Drop the FK constraint from conversations to disputes.
--
-- conversations is a replica table fed by a separate Kafka topic. Conversations
-- can arrive before their parent dispute has been replicated, so a hard FK
-- blocks valid events. We use dispute_id only for indexed lookups; referential
-- integrity is enforced at the source (tms-dispute), not here.
-- ============================================================================

ALTER TABLE conversations DROP CONSTRAINT IF EXISTS conversations_dispute_id_fkey;

-- Also drop the FK from dispute_reads so orphaned read markers don't block
-- the consumer either.
ALTER TABLE dispute_reads DROP CONSTRAINT IF EXISTS dispute_reads_dispute_id_fkey;
