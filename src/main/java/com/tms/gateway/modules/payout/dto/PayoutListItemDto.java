package com.tms.gateway.modules.payout.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

public record PayoutListItemDto(@JsonProperty("payout_id") String payoutId,
        @JsonProperty("payment_reference") String paymentReference, @JsonProperty("total_value") BigDecimal totalValue,
        @JsonProperty("payout_value") BigDecimal payoutValue, @JsonProperty("currency_code") String currencyCode,
        @JsonProperty("receiving_bank") String receivingBank, @JsonProperty("status") String status,
        @JsonProperty("status_display") String statusDisplay, @JsonProperty("sales_date") Instant salesDate,
        @JsonProperty("date_time_settled") Instant dateTimeSettled) {
}
