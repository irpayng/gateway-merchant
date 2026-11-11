package com.tms.gateway.modules.apikeys.service;

import com.tms.gateway.modules.apikeys.dto.ApiKeyListResponse;
import com.tms.gateway.modules.apikeys.dto.ApiKeyResponse;
import com.tms.gateway.modules.apikeys.dto.CreatedApiKeyResponse;
import com.tms.gateway.modules.apikeys.dto.EnableApiAccessResponse;
import com.tms.gateway.modules.grpc.b2b.ApiKeyCreated;
import com.tms.gateway.modules.grpc.b2b.ApiKeyInfo;
import com.tms.gateway.modules.grpc.b2b.B2bCommandResponse;
import com.tms.gateway.modules.grpc.b2b.CreateApiKeyResponse;
import com.tms.gateway.modules.grpc.b2b.GetApiKeysResponse;
import com.tms.gateway.modules.grpc.service.B2bGrpcClient;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for API key management operations. All operations go through gRPC to
 * tms-b2b (source of truth).
 */
@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);

    private final B2bGrpcClient b2bGrpcClient;

    public ApiKeyService(B2bGrpcClient b2bGrpcClient) {
        this.b2bGrpcClient = b2bGrpcClient;
    }

    /**
     * Enable API access for a pg_merchant user. Creates a B2bAccountEntity linked
     * to the existing user and issues the first sandbox key.
     */
    public EnableApiAccessResponse enableApiAccess(long userId, String merchantId, String businessName, String email) {
        log.info("Enabling API access for userId={}, merchantId={}", userId, merchantId);

        var response = b2bGrpcClient.enableApiAccess(userId, merchantId, businessName, email);

        CreatedApiKeyResponse apiKey = null;
        if (response.hasApiKey()) {
            apiKey = toCreatedApiKeyResponse(response.getApiKey());
        }

        return new EnableApiAccessResponse(response.getSuccess(), response.getAccountId(), apiKey);
    }

    /**
     * Get all API keys for a pg_merchant user.
     */
    public ApiKeyListResponse getApiKeys(long userId) {
        log.debug("Getting API keys for userId={}", userId);

        GetApiKeysResponse response = b2bGrpcClient.getApiKeys(userId);

        List<ApiKeyResponse> keys = response.getKeysList().stream().map(this::toApiKeyResponse).toList();

        return new ApiKeyListResponse(keys);
    }

    /**
     * Create a new API key for a pg_merchant user.
     */
    public CreatedApiKeyResponse createApiKey(long userId, String environment) {
        log.info("Creating {} API key for userId={}", environment, userId);

        CreateApiKeyResponse response = b2bGrpcClient.createApiKey(userId, environment);

        return toCreatedApiKeyResponse(response.getApiKey());
    }

    /**
     * Rotate an existing API key — mints a replacement with the same scope.
     */
    public CreatedApiKeyResponse rotateApiKey(long userId, String keyId) {
        log.info("Rotating API key keyId={} for userId={}", keyId, userId);

        CreateApiKeyResponse response = b2bGrpcClient.rotateApiKey(userId, keyId);

        return toCreatedApiKeyResponse(response.getApiKey());
    }

    /**
     * Revoke an API key.
     */
    public void revokeApiKey(long userId, String keyId) {
        log.info("Revoking API key keyId={} for userId={}", keyId, userId);

        B2bCommandResponse response = b2bGrpcClient.revokeApiKey(userId, keyId);

        log.debug("Revoke result: success={}, message={}", response.getSuccess(), response.getMessage());
    }

    // ── Mapping helpers ──

    private ApiKeyResponse toApiKeyResponse(ApiKeyInfo info) {
        return new ApiKeyResponse(emptyToNull(info.getId()), emptyToNull(info.getPublicId()),
                emptyToNull(info.getEnvironment()), emptyToNull(info.getStatus()), emptyToNull(info.getScopes()),
                emptyToNull(info.getPlan()), emptyToNull(info.getCreatedAt()), emptyToNull(info.getLastUsedAt()));
    }

    private CreatedApiKeyResponse toCreatedApiKeyResponse(ApiKeyCreated created) {
        return new CreatedApiKeyResponse(emptyToNull(created.getId()), emptyToNull(created.getPublicId()),
                emptyToNull(created.getSecret()), emptyToNull(created.getEnvironment()),
                emptyToNull(created.getScopes()), emptyToNull(created.getPlan()));
    }

    private String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }
}
