package com.tms.gateway.modules.apikeys.dto;

/**
 * Response DTO for a newly created or rotated API key (includes secret). The
 * secret is shown only once.
 */
public record CreatedApiKeyResponse(String id, String publicId, String secret, String environment, String scopes,
        String plan) {
}
