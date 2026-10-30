package com.tms.gateway.modules.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Registration request for pg_merchant dashboard.
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Size(max = 255, message = "Password must not exceed 255 characters")
    private String password;

    @NotBlank(message = "Phone number is required")
    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    @JsonProperty("phone_number")
    private String phoneNumber;

    @NotBlank(message = "Business name is required")
    @Size(max = 255, message = "Business name must not exceed 255 characters")
    @JsonProperty("business_name")
    private String businessName;

    @NotBlank(message = "Business type is required")
    @Pattern(regexp = "^(starter|registered)$", message = "Business type must be 'starter' or 'registered'")
    @JsonProperty("business_type")
    private String businessType;

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must not exceed 100 characters")
    @JsonProperty("first_name")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    @JsonProperty("last_name")
    private String lastName;

    /**
     * ISO 3166-1 alpha-2 country code. Defaults to "NG" (Nigeria) if not provided.
     */
    @Size(max = 2, message = "Country code must be 2 characters")
    private String country;

    public String getCountry() {
        return (country == null || country.isBlank()) ? "NG" : country.trim().toUpperCase();
    }
}
