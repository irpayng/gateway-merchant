package com.tms.gateway.modules.webhook.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for updating a webhook.
 */
public record UpdateWebhookRequest(@NotBlank(message = "URL is required") String url) {
}
