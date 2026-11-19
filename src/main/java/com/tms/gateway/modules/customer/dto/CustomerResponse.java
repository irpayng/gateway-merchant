package com.tms.gateway.modules.customer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record CustomerResponse(@JsonProperty("customer_ref") String customerRef,
        @JsonProperty("customer_id") String customerId, @JsonProperty("email") String email,
        @JsonProperty("full_name") String fullName, @JsonProperty("phone_number") String phoneNumber,
        @JsonProperty("date_added") Instant dateAdded, @JsonProperty("blacklisted") boolean blacklisted) {
}
