package com.tms.gateway.modules.apikeys.dto;

/**
 * Webhook DTO for listing webhooks.
 */
public record WebhookDto(String id, String environment, String url, String createdAt, String updatedAt) {
}
