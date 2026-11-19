package com.tms.gateway.modules.dispute.service;

import com.tms.gateway.core.dto.PagedResponse;
import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.modules.dispute.dto.*;
import com.tms.gateway.modules.dispute.dto.DisputeFiltersDto.FilterOption;
import com.tms.gateway.modules.dispute.entity.ConversationEntity;
import com.tms.gateway.modules.dispute.entity.DisputeEntity;
import com.tms.gateway.modules.dispute.repository.ConversationRepository;
import com.tms.gateway.modules.dispute.repository.DisputeReadRepository;
import com.tms.gateway.modules.dispute.repository.DisputeRepository;
import com.tms.gateway.modules.grpc.service.DisputeGrpcClient;
import com.tms.gateway.modules.transaction.entity.PgMerchantTransactionEntity;
import com.tms.gateway.modules.transaction.repository.PgMerchantTransactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DisputeService {

    private static final String BASE_PATH = "/disputes";
    private static final String NEVER_READ = "1970-01-01 00:00:00";

    private final DisputeRepository disputeRepository;
    private final ConversationRepository conversationRepository;
    private final DisputeReadRepository disputeReadRepository;
    private final DisputeGrpcClient disputeGrpcClient;
    private final PgMerchantTransactionRepository transactionRepository;
    private final EntityManager entityManager;

    // Static filter options
    private static final List<FilterOption> STATUS_OPTIONS = List.of(new FilterOption("open", "Open"),
            new FilterOption("processing", "Processing"), new FilterOption("resolved", "Resolved"),
            new FilterOption("closed", "Closed"));

    private static final DisputeFiltersDto FILTERS = new DisputeFiltersDto(STATUS_OPTIONS);

    /**
     * Create a new dispute.
     */
    public Map<String, Object> create(GatewayMerchantUserDetails user, CreateDisputeRequest request) {
        return disputeGrpcClient.createDispute(user.getUserId(), request.getTransactionReference(),
                request.getSubject(), request.getMessage(), user.getDisplayName(), user.getEmail());
    }

    /**
     * Add a message to an existing dispute.
     */
    public Map<String, Object> addConversation(GatewayMerchantUserDetails user, Long disputeId, String message) {
        // Verify ownership
        if (disputeRepository.findByIdAndUserId(disputeId, user.getUserId()).isEmpty()) {
            return Map.of("success", false, "message", "Dispute not found.");
        }

        return disputeGrpcClient.addUserConversation(disputeId, user.getUserId(), message, user.getDisplayName());
    }

    /**
     * Close a dispute.
     */
    public Map<String, Object> close(GatewayMerchantUserDetails user, Long disputeId) {
        // Verify ownership
        if (disputeRepository.findByIdAndUserId(disputeId, user.getUserId()).isEmpty()) {
            return Map.of("success", false, "message", "Dispute not found.");
        }

        return disputeGrpcClient.closeDispute(disputeId, user.getUserId(), user.getDisplayName(), user.getEmail());
    }

    /**
     * Get paginated list of disputes with filters.
     *
     * @param page
     *            1-indexed page number
     */
    @Transactional(readOnly = true)
    public Map<String, Object> list(Long userId, String status, String search, Instant startDate, Instant endDate,
            int page, int size) {
        int zeroIndexedPage = Math.max(0, page - 1);
        Pageable pageable = PageRequest.of(zeroIndexedPage, size);

        Page<DisputeEntity> result = disputeRepository.findFiltered(userId, trimToNull(search), trimToNull(status),
                startDate, endDate, pageable);

        List<Map<String, Object>> data = result.getContent().stream().map(this::toListView).toList();

        return PagedResponse.from(data, result.getTotalElements(), page, size, BASE_PATH, Map.of("filters", FILTERS));
    }

    /**
     * Get filter options.
     */
    public DisputeFiltersDto filters() {
        return FILTERS;
    }

    /**
     * Get dispute threads for inbox/messenger UI.
     */
    @Transactional(readOnly = true)
    public List<DisputeThreadDto> threads(Long userId, String filter, String search, int limit, int offset) {
        Map<String, Object> params = new LinkedHashMap<>();
        StringBuilder where = new StringBuilder(" WHERE d.user_id = :userId ");
        params.put("userId", userId);

        boolean unreadOnly = "unread".equalsIgnoreCase(filter);

        // Status filter
        if (filter != null && !filter.isBlank() && !"all".equalsIgnoreCase(filter) && !unreadOnly) {
            where.append(" AND d.status_code = :status ");
            params.put("status", filter);
        }

        // Search filter
        if (search != null && !search.isBlank()) {
            String trimmed = search.trim();
            if (trimmed.matches("\\d+")) {
                where.append(" AND (d.id = :exactId OR d.subject ILIKE :q OR d.transaction_reference ILIKE :q) ");
                params.put("exactId", Long.parseLong(trimmed));
            } else {
                where.append(" AND (d.subject ILIKE :q OR d.transaction_reference ILIKE :q) ");
            }
            params.put("q", "%" + trimmed + "%");
        }

        // Unread filter
        if (unreadOnly) {
            where.append(" AND COALESCE(uc.cnt, 0) > 0 ");
        }

        String sql = """
                SELECT d.id, d.subject, d.status_code,
                    lm.message, lm.sender_type, lm.created_at AS last_message_at, lm.attachment_key,
                    COALESCE(uc.cnt, 0) AS unread_count, d.created_at
                FROM disputes d
                LEFT JOIN dispute_reads dr ON dr.dispute_id = d.id AND dr.user_id = :userId
                LEFT JOIN LATERAL (
                    SELECT c.message, c.sender_type, c.created_at, c.attachment_key
                    FROM conversations c WHERE c.dispute_id = d.id
                    ORDER BY c.created_at DESC, c.id DESC LIMIT 1
                ) lm ON TRUE
                LEFT JOIN LATERAL (
                    SELECT COUNT(*) AS cnt FROM conversations c2
                    WHERE c2.dispute_id = d.id AND c2.sender_type IN ('admin', 'agent')
                      AND c2.created_at > COALESCE(dr.last_read_at, TIMESTAMP '%s')
                ) uc ON TRUE
                """.formatted(NEVER_READ) + where + " ORDER BY COALESCE(lm.created_at, d.created_at) DESC "
                + "LIMIT :limit OFFSET :offset";

        Query q = entityManager.createNativeQuery(sql);
        q.setParameter("limit", Math.max(1, Math.min(limit, 200)));
        q.setParameter("offset", Math.max(0, offset));
        params.forEach(q::setParameter);

        List<DisputeThreadDto> out = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();

        for (Object[] r : rows) {
            String attachmentKey = str(r[6]);
            out.add(new DisputeThreadDto(lng(r[0]), // id
                    str(r[1]), // reason (subject)
                    str(r[2]), // status_code
                    str(r[3]), // last_message
                    normalizeSender(str(r[4])), // last_message_sender
                    str(r[5]), // last_message_at
                    attachmentKey != null, // last_message_has_attachment
                    r[7] != null ? ((Number) r[7]).longValue() : 0L, // unread_count
                    str(r[8]) // created_at
            ));
        }

        return out;
    }

    /**
     * Get total unread count for a user.
     */
    @Transactional(readOnly = true)
    public long totalUnread(Long userId) {
        if (userId == null) {
            return 0L;
        }
        return conversationRepository.countTotalUnread(userId);
    }

    /**
     * Mark a dispute as read.
     */
    @Transactional
    public void markRead(Long userId, Long disputeId) {
        if (userId == null || disputeId == null) {
            return;
        }
        disputeReadRepository.upsertReadMarker(userId, disputeId);
    }

    /**
     * Get single dispute detail with conversations.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> show(Long userId, Long disputeId) {
        DisputeEntity dispute = disputeRepository.findByIdAndUserId(disputeId, userId).orElse(null);
        if (dispute == null) {
            return null;
        }

        Map<String, Object> view = toDetailView(dispute);

        // Fetch transaction if reference exists
        String txnRef = dispute.getTransactionReference();
        if (txnRef != null && !txnRef.isBlank()) {
            transactionRepository.findByUserIdAndReference(userId, txnRef)
                    .ifPresent(txn -> view.put("transaction", mapTransaction(txn)));
        }

        // Fetch conversations
        List<ConversationEntity> conversations = conversationRepository.findByDisputeIdOrderByCreatedAtAsc(disputeId);
        view.put("conversations", conversations.stream().map(this::conversationToView).toList());

        return view;
    }

    // ── Mapping helpers ─────────────────────────────────────────────────────

    private Map<String, Object> toListView(DisputeEntity d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("transaction_reference", d.getTransactionReference());
        m.put("subject", d.getSubject());
        m.put("reason", d.getSubject());
        m.put("status_code", d.getStatusCode());
        m.put("status", Map.of("code", d.getStatusCode() != null ? d.getStatusCode() : "", "description",
                d.getStatusDescription() != null ? d.getStatusDescription() : ""));
        m.put("created_at", d.getCreatedAt() != null ? d.getCreatedAt().toString() : null);
        m.put("resolved_at", d.getResolvedAt() != null ? d.getResolvedAt().toString() : null);
        return m;
    }

    private Map<String, Object> toDetailView(DisputeEntity d) {
        Map<String, Object> m = toListView(d);
        m.put("message", d.getMessage());
        m.put("attachment_url", d.getAttachmentUrl());
        m.put("updated_at", d.getUpdatedAt() != null ? d.getUpdatedAt().toString() : null);
        return m;
    }

    private Map<String, Object> conversationToView(ConversationEntity c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("user_id", c.getUserId());
        m.put("sender_type", normalizeSender(c.getSenderType()));
        m.put("sender_name", c.getSenderName());
        m.put("dispute_id", c.getDisputeId());
        m.put("message", c.getMessage());
        m.put("created_at", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);

        // Attachment handling
        if (c.getAttachmentKey() != null && !c.getAttachmentKey().isBlank()) {
            Map<String, Object> attachment = new LinkedHashMap<>();
            attachment.put("url", c.getAttachmentKey());
            attachment.put("name", c.getAttachmentName());
            attachment.put("type", c.getAttachmentType());
            attachment.put("size", c.getAttachmentSize());
            attachment.put("is_image",
                    c.getAttachmentType() != null && c.getAttachmentType().toLowerCase().startsWith("image/"));
            m.put("attachment", attachment);
        }

        return m;
    }

    private Map<String, Object> mapTransaction(PgMerchantTransactionEntity txn) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reference", txn.getReference());
        m.put("amount", txn.getAmount() != null ? txn.getAmount().toPlainString() : null);
        m.put("status",
                txn.getStatusCode() != null ? Map.of("code", txn.getStatusCode(), "name", txn.getStatusCode()) : null);
        m.put("product", txn.getProductCode());
        m.put("created_at", txn.getCreatedAt() != null ? txn.getCreatedAt().toString() : null);
        return m;
    }

    private static String normalizeSender(String senderType) {
        if (senderType == null || senderType.isBlank()) {
            return null;
        }
        return "agent".equals(senderType) ? "admin" : senderType;
    }

    private static String trimToNull(String value) {
        if (value == null)
            return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Long lng(Object o) {
        return o != null ? ((Number) o).longValue() : null;
    }

    private static String str(Object o) {
        return o != null ? o.toString().trim() : null;
    }
}
