package com.tms.gateway.modules.webhook.dto;

import java.util.List;

/**
 * Response DTO for listing webhooks.
 */
public record WebhookListResponse(List<WebhookResponse> webhooks) {
}
