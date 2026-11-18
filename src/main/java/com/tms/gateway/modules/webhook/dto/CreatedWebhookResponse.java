package com.tms.gateway.modules.webhook.dto;

/**
 * Response DTO for a newly created webhook (includes secret, shown once).
 */
public record CreatedWebhookResponse(String id, String environment, String url, String secret) {
}
