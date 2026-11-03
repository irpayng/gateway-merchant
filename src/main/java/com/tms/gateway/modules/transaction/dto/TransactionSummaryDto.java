package com.tms.gateway.modules.transaction.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * Transaction summary statistics for dashboard cards.
 */
public record TransactionSummaryDto(@JsonProperty("total_count") long totalCount,
        @JsonProperty("total_value") BigDecimal totalValue, @JsonProperty("successful_count") long successfulCount,
        @JsonProperty("successful_value") BigDecimal successfulValue, @JsonProperty("failed_count") long failedCount,
        @JsonProperty("failed_value") BigDecimal failedValue, @JsonProperty("pending_count") long pendingCount,
        @JsonProperty("pending_value") BigDecimal pendingValue) {
}
