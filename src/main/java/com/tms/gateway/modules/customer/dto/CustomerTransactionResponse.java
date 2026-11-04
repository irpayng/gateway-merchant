package com.tms.gateway.modules.customer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

public record CustomerTransactionResponse(@JsonProperty("reference") String reference,
        @JsonProperty("amount") BigDecimal amount, @JsonProperty("currency") String currency,
        @JsonProperty("payment_method") String paymentMethod,
        @JsonProperty("payment_method_display") String paymentMethodDisplay, @JsonProperty("status") String status,
        @JsonProperty("status_display") String statusDisplay, @JsonProperty("created_at") Instant createdAt) {
}
