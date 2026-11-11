package com.tms.gateway.modules.apikeys.controller;

import com.tms.gateway.core.dto.ApiResponse;
import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.modules.apikeys.dto.ApiKeyListResponse;
import com.tms.gateway.modules.apikeys.dto.CreateApiKeyRequest;
import com.tms.gateway.modules.apikeys.dto.CreatedApiKeyResponse;
import com.tms.gateway.modules.apikeys.dto.EnableApiAccessResponse;
import com.tms.gateway.modules.apikeys.service.ApiKeyService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
 * <li>POST /api-keys/enable - Enable API access for the logged-in merchant</li>
 * <li>GET /api-keys - List all API keys</li>
 * <li>POST /api-keys - Create a new API key</li>
 * <li>POST /api-keys/{id}/rotate - Rotate an existing key</li>
 * <li>DELETE /api-keys/{id} - Revoke a key</li>
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
     * Enable API access for the logged-in merchant. Creates a B2bAccountEntity
     * linked to the existing pg_merchant user and issues the first sandbox key.
     * Idempotent — if already enabled, returns existing account info.
     */
    @PostMapping("/enable")
    public ResponseEntity<ApiResponse<EnableApiAccessResponse>> enableApiAccess(
            @AuthenticationPrincipal GatewayMerchantUserDetails user) {

        EnableApiAccessResponse response = apiKeyService.enableApiAccess(user.getUserId(), user.getMerchantId(),
                user.getBusinessName(), user.getEmail());

        return ResponseEntity.ok(ApiResponse.success(response, "API access enabled"));
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
     * Create a new API key for the logged-in merchant. Live keys require KYC
     * approval.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CreatedApiKeyResponse>> createApiKey(
            @AuthenticationPrincipal GatewayMerchantUserDetails user, @Valid @RequestBody CreateApiKeyRequest request) {

        CreatedApiKeyResponse response = apiKeyService.createApiKey(user.getUserId(), request.environment());

        return ResponseEntity.ok(ApiResponse.success(response, "API key created"));
    }

    /**
     * Rotate an existing API key — mints a replacement with the same scope.
     */
    @PostMapping("/{id}/rotate")
    public ResponseEntity<ApiResponse<CreatedApiKeyResponse>> rotateApiKey(
            @AuthenticationPrincipal GatewayMerchantUserDetails user, @PathVariable("id") String keyId) {

        CreatedApiKeyResponse response = apiKeyService.rotateApiKey(user.getUserId(), keyId);

        return ResponseEntity.ok(ApiResponse.success(response, "API key rotated"));
    }

    /**
     * Revoke an API key.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> revokeApiKey(@AuthenticationPrincipal GatewayMerchantUserDetails user,
            @PathVariable("id") String keyId) {

        apiKeyService.revokeApiKey(user.getUserId(), keyId);

        return ResponseEntity.ok(ApiResponse.success(null, "API key revoked"));
    }
}
