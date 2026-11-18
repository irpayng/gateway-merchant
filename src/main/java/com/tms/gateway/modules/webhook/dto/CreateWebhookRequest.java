package com.tms.gateway.modules.webhook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for creating a webhook.
 */
public record CreateWebhookRequest(
        @NotBlank(message = "Environment is required") @Pattern(regexp = "^(sandbox|live)$", message = "Environment must be 'sandbox' or 'live'") String environment,

        @NotBlank(message = "URL is required") String url) {
}
