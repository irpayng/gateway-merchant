package com.tms.gateway.modules.refund.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record RefundPageResponse(@JsonProperty("content") List<RefundListItemDto> content,
        @JsonProperty("page") int page, @JsonProperty("size") int size,
        @JsonProperty("total_elements") long totalElements, @JsonProperty("total_pages") int totalPages,
        @JsonProperty("has_next") boolean hasNext, @JsonProperty("has_previous") boolean hasPrevious) {
}
