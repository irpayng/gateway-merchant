package com.tms.gateway.modules.transaction.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Laravel-style paginated response for transaction list.
 */
public record TransactionPageResponse(@JsonProperty("data") List<TransactionListItemDto> data,
        @JsonProperty("links") PaginationLinks links, @JsonProperty("meta") PaginationMeta meta,
        @JsonProperty("filters") TransactionFiltersDto filters) {

    public record PaginationLinks(@JsonProperty("first") String first, @JsonProperty("last") String last,
            @JsonProperty("prev") String prev, @JsonProperty("next") String next) {
    }

    public record PaginationMeta(@JsonProperty("current_page") int currentPage, @JsonProperty("from") Integer from,
            @JsonProperty("last_page") int lastPage, @JsonProperty("links") List<PageLink> links,
            @JsonProperty("path") String path, @JsonProperty("per_page") int perPage, @JsonProperty("to") Integer to,
            @JsonProperty("total") long total) {
    }

    public record PageLink(@JsonProperty("url") String url, @JsonProperty("label") String label,
            @JsonProperty("active") boolean active) {
    }
}
