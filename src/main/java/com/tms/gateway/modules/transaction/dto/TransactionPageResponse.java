package com.tms.gateway.modules.transaction.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Paginated response for transaction list.
 */
public record TransactionPageResponse(@JsonProperty("content") List<TransactionListItemDto> content,
        @JsonProperty("page") int page, @JsonProperty("size") int size,
        @JsonProperty("total_elements") long totalElements, @JsonProperty("total_pages") int totalPages,
        @JsonProperty("has_next") boolean hasNext, @JsonProperty("has_previous") boolean hasPrevious) {
}
