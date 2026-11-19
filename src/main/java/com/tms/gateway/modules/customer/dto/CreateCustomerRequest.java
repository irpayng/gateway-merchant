package com.tms.gateway.modules.customer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCustomerRequest(@JsonProperty("first_name") @NotBlank @Size(max = 100) String firstName,
        @JsonProperty("last_name") @NotBlank @Size(max = 100) String lastName,
        @JsonProperty("email") @NotBlank @Email @Size(max = 254) String email,
        @JsonProperty("phone_number") @Pattern(regexp = "^\\+[1-9]\\d{1,14}$") String phoneNumber) {
}
