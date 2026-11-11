package com.tms.gateway.modules.apikeys.dto;

import java.util.List;

/**
 * Response DTO for listing API keys.
 */
public record ApiKeyListResponse(List<ApiKeyResponse> keys) {
}
