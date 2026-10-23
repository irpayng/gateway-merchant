package com.tms.gateway.modules.compliance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request to save step 2: Contact information.
 */
public record SaveContactRequest(
        @NotBlank(message = "Email is required") @Email(message = "Invalid email format") String email,

        @NotBlank(message = "Phone is required") String phone,

        @NotBlank(message = "Address is required") String address,

        @NotBlank(message = "City is required") String city,

        @NotBlank(message = "State is required") String state,

        @JsonProperty("postal_code") String postalCode,

        String country) {
}
