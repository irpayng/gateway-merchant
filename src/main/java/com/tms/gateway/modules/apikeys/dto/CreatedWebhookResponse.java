package com.tms.gateway.modules.apikeys.dto;

/**
 * Response containing the newly created webhook with secret shown once.
 */
public record CreatedWebhookResponse(String id, String environment, String url, String secret) {
}
