package com.tms.gateway.modules.webhook.dto;

/**
 * Response DTO for a webhook (without secret).
 */
public record WebhookResponse(String id, String environment, String url, String createdAt, String updatedAt) {
}
