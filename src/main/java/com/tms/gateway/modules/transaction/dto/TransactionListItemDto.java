package com.tms.gateway.modules.transaction.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Transaction list item for dashboard table.
 */
public record TransactionListItemDto(@JsonProperty("reference") String reference,
        @JsonProperty("amount") BigDecimal amount, @JsonProperty("service_fee") BigDecimal serviceFee,
        @JsonProperty("payment_method") String paymentMethod,
        @JsonProperty("payment_method_display") String paymentMethodDisplay,
        @JsonProperty("customer_email") String customerEmail, @JsonProperty("customer_name") String customerName,
        @JsonProperty("status") String status, @JsonProperty("status_display") String statusDisplay,
        @JsonProperty("created_at") Instant createdAt) {
}
