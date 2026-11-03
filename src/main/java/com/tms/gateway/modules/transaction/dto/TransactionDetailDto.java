package com.tms.gateway.modules.transaction.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Transaction detail for single transaction view.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionDetailDto(@JsonProperty("reference") String reference,
        @JsonProperty("amount") BigDecimal amount, @JsonProperty("service_fee") BigDecimal serviceFee,
        @JsonProperty("product_code") String productCode, @JsonProperty("channel") String channel,
        @JsonProperty("payment_method") String paymentMethod,
        @JsonProperty("payment_method_display") String paymentMethodDisplay,
        @JsonProperty("customer_email") String customerEmail, @JsonProperty("customer_name") String customerName,
        @JsonProperty("bank_name") String bankName, @JsonProperty("account_number") String accountNumber,
        @JsonProperty("status") String status, @JsonProperty("status_display") String statusDisplay,
        @JsonProperty("status_message") String statusMessage,
        @JsonProperty("provider_reference") String providerReference, @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt) {
}
