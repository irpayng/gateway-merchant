package com.tms.gateway.modules.apikeys.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to update a webhook URL.
 */
public record UpdateWebhookRequest(@NotBlank(message = "url is required") String url) {
}
