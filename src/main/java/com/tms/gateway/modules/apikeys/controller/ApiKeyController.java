package com.tms.gateway.modules.apikeys.controller;

import com.tms.gateway.core.dto.ApiResponse;
import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.modules.apikeys.dto.ApiKeyListResponse;
import com.tms.gateway.modules.apikeys.dto.CreatedApiKeyResponse;
import com.tms.gateway.modules.apikeys.dto.UpdateIpWhitelistRequest;
import com.tms.gateway.modules.apikeys.service.ApiKeyService;
import com.tms.gateway.modules.audit.annotation.Auditable;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for API key management.
 *
 * <p>
 * Endpoints:
 * <ul>
 * <li>GET /api-keys - List all API keys</li>
 * <li>POST /api-keys/{id}/rotate - Rotate an existing key</li>
 * <li>DELETE /api-keys/{id} - Revoke a key</li>
 * <li>PATCH /api-keys/{id}/ip-whitelist - Update IP whitelist for a key</li>
 * </ul>
 */
@RestController
@RequestMapping("/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    /**
     * List all API keys for the logged-in merchant.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<ApiKeyListResponse>> listApiKeys(
            @AuthenticationPrincipal GatewayMerchantUserDetails user) {

        ApiKeyListResponse response = apiKeyService.getApiKeys(user.getUserId());

        return ResponseEntity.ok(ApiResponse.success(response, "API keys retrieved"));
    }

    /**
     * Rotate an existing API key — mints a replacement with the same scope.
     */
    @Auditable("Rotate API key")
    @PostMapping("/{id}/rotate")
    public ResponseEntity<ApiResponse<CreatedApiKeyResponse>> rotateApiKey(
            @AuthenticationPrincipal GatewayMerchantUserDetails user, @PathVariable("id") String keyId) {

        CreatedApiKeyResponse response = apiKeyService.rotateApiKey(user.getUserId(), keyId);

        return ResponseEntity.ok(ApiResponse.success(response, "API key rotated"));
    }

    /**
     * Revoke an API key.
     */
    @Auditable("Revoke API key")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> revokeApiKey(@AuthenticationPrincipal GatewayMerchantUserDetails user,
            @PathVariable("id") String keyId) {

        apiKeyService.revokeApiKey(user.getUserId(), keyId);

        return ResponseEntity.ok(ApiResponse.success(null, "API key revoked"));
    }

    /**
     * Update IP whitelist for an API key.
     */
    @Auditable("Update API key IP whitelist")
    @PatchMapping("/{id}/ip-whitelist")
    public ResponseEntity<ApiResponse<Void>> updateIpWhitelist(@AuthenticationPrincipal GatewayMerchantUserDetails user,
            @PathVariable("id") String keyId, @Valid @RequestBody UpdateIpWhitelistRequest request) {

        apiKeyService.updateIpWhitelist(user.getUserId(), keyId, request.ipWhitelist());

        return ResponseEntity.ok(ApiResponse.success(null, "IP whitelist updated"));
    }
}
