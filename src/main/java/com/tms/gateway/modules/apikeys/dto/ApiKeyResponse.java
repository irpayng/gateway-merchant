package com.tms.gateway.modules.apikeys.dto;

/**
 * Response DTO for an API key.
 */
public record ApiKeyResponse(String id, String publicId, String secret, String environment, String status,
        String scopes, String plan, String createdAt, String lastUsedAt, String ipWhitelist) {
}
