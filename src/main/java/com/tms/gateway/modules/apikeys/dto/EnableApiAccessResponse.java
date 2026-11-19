package com.tms.gateway.modules.apikeys.dto;

/**
 * Response DTO for enabling API access.
 */
public record EnableApiAccessResponse(boolean apiEnabled, Long accountId, CreatedApiKeyResponse apiKey) {
}
