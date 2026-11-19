package com.tms.gateway.modules.apikeys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request to create a webhook.
 */
public record CreateWebhookRequest(
        @NotBlank(message = "environment is required") @Pattern(regexp = "sandbox|live", message = "environment must be 'sandbox' or 'live'") String environment,
        @NotBlank(message = "url is required") String url) {
}
