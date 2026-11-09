package com.tms.gateway.modules.refund.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

public record RefundListItemDto(@JsonProperty("refund_id") String refundId,
        @JsonProperty("amount") BigDecimal amount, @JsonProperty("currency_code") String currencyCode,
        @JsonProperty("customer_name") String customerName, @JsonProperty("customer_email") String customerEmail,
        @JsonProperty("status") String status, @JsonProperty("status_display") String statusDisplay,
        @JsonProperty("created_at") Instant createdAt) {
}
