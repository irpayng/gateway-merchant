package com.tms.gateway.modules.apikeys.dto;

/**
 * Response DTO for an API key (without secret).
 */
public record ApiKeyResponse(String id, String publicId, String environment, String status, String scopes, String plan,
        String createdAt, String lastUsedAt, String ipWhitelist) {
}
