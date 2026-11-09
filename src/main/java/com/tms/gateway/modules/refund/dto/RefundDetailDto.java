package com.tms.gateway.modules.refund.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

public record RefundDetailDto(@JsonProperty("refund_id") String refundId,
        @JsonProperty("amount") BigDecimal amount, @JsonProperty("currency_code") String currencyCode,
        @JsonProperty("status") String status, @JsonProperty("status_display") String statusDisplay,
        @JsonProperty("transaction_reference") String transactionReference,
        @JsonProperty("refund_type") String refundType, @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("refunded_at") Instant refundedAt,
        @JsonProperty("refund_initiated_by") String refundInitiatedBy,
        @JsonProperty("refund_channel") String refundChannel,
        @JsonProperty("merchant_name") String merchantName,
        @JsonProperty("merchant_email") String merchantEmail,
        @JsonProperty("merchant_id") String merchantId) {
}
