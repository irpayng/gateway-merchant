package com.tms.gateway.modules.customer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record CustomerTransactionPageResponse(List<CustomerTransactionResponse> content, int page, int size,
        @JsonProperty("total_elements") long totalElements, @JsonProperty("total_pages") int totalPages,
        @JsonProperty("has_next") boolean hasNext, @JsonProperty("has_previous") boolean hasPrevious) {
}
