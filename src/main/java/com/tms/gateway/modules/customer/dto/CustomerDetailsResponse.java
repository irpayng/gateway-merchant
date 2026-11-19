package com.tms.gateway.modules.customer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

public record CustomerDetailsResponse(@JsonProperty("customer_ref") String customerRef,
        @JsonProperty("customer_id") String customerId, @JsonProperty("email") String email,
        @JsonProperty("full_name") String fullName, @JsonProperty("phone_number") String phoneNumber,
        @JsonProperty("date_added") Instant dateAdded, @JsonProperty("blacklisted") boolean blacklisted,
        @JsonProperty("total_spend") BigDecimal totalSpend, @JsonProperty("currency") String currency,
        @JsonProperty("successful_count") long successfulCount, @JsonProperty("total_count") long totalCount) {
}
