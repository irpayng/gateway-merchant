package com.tms.gateway.modules.apikeys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for creating a new API key.
 */
public record CreateApiKeyRequest(
        @NotBlank(message = "Environment is required") @Pattern(regexp = "sandbox|live", message = "Environment must be 'sandbox' or 'live'") String environment) {
}
