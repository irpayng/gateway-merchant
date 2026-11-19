package com.tms.gateway.modules.grpc.service;

import com.b2b.grpc.B2bCommandResponse;
import com.b2b.grpc.BusinessServiceGrpc;
import com.b2b.grpc.CreateApiKeyResponse;
import com.b2b.grpc.CreateWebhookRequest;
import com.b2b.grpc.CreateWebhookResponse;
import com.b2b.grpc.DeleteWebhookRequest;
import com.b2b.grpc.DeleteWebhookResponse;
import com.b2b.grpc.EnableApiAccessRequest;
import com.b2b.grpc.EnableApiAccessResponse;
import com.b2b.grpc.GetApiKeysRequest;
import com.b2b.grpc.GetApiKeysResponse;
import com.b2b.grpc.GetWebhooksRequest;
import com.b2b.grpc.GetWebhooksResponse;
import com.b2b.grpc.RevokeApiKeyRequest;
import com.b2b.grpc.RotateApiKeyRequest;
import com.b2b.grpc.TestWebhookRequest;
import com.b2b.grpc.TestWebhookResponse;
import com.b2b.grpc.UpdateApiKeyIpWhitelistRequest;
import com.b2b.grpc.UpdateApiKeyIpWhitelistResponse;
import com.b2b.grpc.UpdateWebhookRequest;
import com.b2b.grpc.UpdateWebhookResponse;
import com.tms.gateway.core.exception.GrpcException;
import com.tms.gateway.modules.grpc.config.GrpcProperties;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * gRPC client for B2B API key management operations in tms-b2b.
 */
@Service
public class B2bGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(B2bGrpcClient.class);
    private static final String SERVICE_NAME = "b2b-service";

    private final GrpcProperties grpcProperties;
    private ManagedChannel channel;
    private BusinessServiceGrpc.BusinessServiceBlockingStub stub;

    public B2bGrpcClient(GrpcProperties grpcProperties) {
        this.grpcProperties = grpcProperties;
    }

    @PostConstruct
    public void init() {
        String host = grpcProperties.getB2bServiceHost();
        int port = grpcProperties.getB2bServicePort();
        log.info("Initializing B2bGrpcClient connecting to {}:{}", host, port);

        channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        stub = BusinessServiceGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Enable API access for a pg_merchant user. Creates a B2bAccountEntity linked
     * to the existing user and issues the first sandbox key.
     */
    public EnableApiAccessResponse enableApiAccess(long userId, String merchantId, String companyName,
            String businessEmail) {
        try {
            EnableApiAccessResponse response = stub.enableApiAccess(EnableApiAccessRequest.newBuilder()
                    .setUserId(userId).setMerchantId(nullToEmpty(merchantId)).setCompanyName(nullToEmpty(companyName))
                    .setBusinessEmail(nullToEmpty(businessEmail)).build());

            if (!response.getSuccess()) {
                throw new GrpcException(SERVICE_NAME, "enableApiAccess", "FAILED", response.getMessage());
            }
            return response;
        } catch (StatusRuntimeException e) {
            log.error("gRPC error in enableApiAccess: {}", e.getStatus());
            throw new GrpcException(SERVICE_NAME, "enableApiAccess", e.getStatus().getCode().name(),
                    "Failed to enable API access: " + e.getStatus().getDescription());
        }
    }

    /**
     * Get all API keys for a pg_merchant user.
     */
    public GetApiKeysResponse getApiKeys(long userId) {
        try {
            GetApiKeysResponse response = stub.getApiKeys(GetApiKeysRequest.newBuilder().setUserId(userId).build());

            if (!response.getSuccess()) {
                throw new GrpcException(SERVICE_NAME, "getApiKeys", "FAILED", response.getMessage());
            }
            return response;
        } catch (StatusRuntimeException e) {
            log.error("gRPC error in getApiKeys: {}", e.getStatus());
            throw new GrpcException(SERVICE_NAME, "getApiKeys", e.getStatus().getCode().name(),
                    "Failed to get API keys: " + e.getStatus().getDescription());
        }
    }

    /**
     * Rotate an existing API key — mints a replacement with the same scope.
     */
    public CreateApiKeyResponse rotateApiKey(long userId, String keyId) {
        try {
            CreateApiKeyResponse response = stub.rotateApiKey(
                    RotateApiKeyRequest.newBuilder().setUserId(userId).setKeyId(nullToEmpty(keyId)).build());

            if (!response.getSuccess()) {
                throw new GrpcException(SERVICE_NAME, "rotateApiKey", "FAILED", response.getMessage());
            }
            return response;
        } catch (StatusRuntimeException e) {
            log.error("gRPC error in rotateApiKey: {}", e.getStatus());
            throw new GrpcException(SERVICE_NAME, "rotateApiKey", e.getStatus().getCode().name(),
                    "Failed to rotate API key: " + e.getStatus().getDescription());
        }
    }

    /**
     * Revoke an API key.
     */
    public B2bCommandResponse revokeApiKey(long userId, String keyId) {
        try {
            B2bCommandResponse response = stub.revokeApiKey(
                    RevokeApiKeyRequest.newBuilder().setUserId(userId).setKeyId(nullToEmpty(keyId)).build());

            if (!response.getSuccess()) {
                throw new GrpcException(SERVICE_NAME, "revokeApiKey", "FAILED", response.getMessage());
            }
            return response;
        } catch (StatusRuntimeException e) {
            log.error("gRPC error in revokeApiKey: {}", e.getStatus());
            throw new GrpcException(SERVICE_NAME, "revokeApiKey", e.getStatus().getCode().name(),
                    "Failed to revoke API key: " + e.getStatus().getDescription());
        }
    }

    /**
     * Update IP whitelist for an API key.
     */
    public void updateIpWhitelist(long userId, String keyId, String ipWhitelist) {
        try {
            UpdateApiKeyIpWhitelistResponse response = stub
                    .updateApiKeyIpWhitelist(UpdateApiKeyIpWhitelistRequest.newBuilder().setUserId(userId)
                            .setKeyId(nullToEmpty(keyId)).setIpWhitelist(nullToEmpty(ipWhitelist)).build());

            if (!response.getSuccess()) {
                throw new GrpcException(SERVICE_NAME, "updateIpWhitelist", "FAILED", response.getMessage());
            }
        } catch (StatusRuntimeException e) {
            log.error("gRPC error in updateIpWhitelist: {}", e.getStatus());
            throw new GrpcException(SERVICE_NAME, "updateIpWhitelist", e.getStatus().getCode().name(),
                    "Failed to update IP whitelist: " + e.getStatus().getDescription());
        }
    }

    // ── Webhook management ──────────────────────────────────────

    /**
     * Get all webhooks for a pg_merchant user.
     */
    public GetWebhooksResponse getWebhooks(long userId) {
        try {
            GetWebhooksResponse response = stub.getWebhooks(GetWebhooksRequest.newBuilder().setUserId(userId).build());

            if (!response.getSuccess()) {
                throw new GrpcException(SERVICE_NAME, "getWebhooks", "FAILED", response.getMessage());
            }
            return response;
        } catch (StatusRuntimeException e) {
            log.error("gRPC error in getWebhooks: {}", e.getStatus());
            throw new GrpcException(SERVICE_NAME, "getWebhooks", e.getStatus().getCode().name(),
                    "Failed to get webhooks: " + e.getStatus().getDescription());
        }
    }

    /**
     * Create a new webhook endpoint.
     */
    public CreateWebhookResponse createWebhook(long userId, String environment, String url) {
        try {
            CreateWebhookResponse response = stub.createWebhook(CreateWebhookRequest.newBuilder().setUserId(userId)
                    .setEnvironment(nullToEmpty(environment)).setUrl(nullToEmpty(url)).build());

            if (!response.getSuccess()) {
                throw new GrpcException(SERVICE_NAME, "createWebhook", "FAILED", response.getMessage());
            }
            return response;
        } catch (StatusRuntimeException e) {
            log.error("gRPC error in createWebhook: {}", e.getStatus());
            throw new GrpcException(SERVICE_NAME, "createWebhook", e.getStatus().getCode().name(),
                    "Failed to create webhook: " + e.getStatus().getDescription());
        }
    }

    /**
     * Update an existing webhook.
     */
    public UpdateWebhookResponse updateWebhook(long userId, String webhookId, String url) {
        try {
            UpdateWebhookResponse response = stub.updateWebhook(UpdateWebhookRequest.newBuilder().setUserId(userId)
                    .setWebhookId(nullToEmpty(webhookId)).setUrl(nullToEmpty(url)).build());

            if (!response.getSuccess()) {
                throw new GrpcException(SERVICE_NAME, "updateWebhook", "FAILED", response.getMessage());
            }
            return response;
        } catch (StatusRuntimeException e) {
            log.error("gRPC error in updateWebhook: {}", e.getStatus());
            throw new GrpcException(SERVICE_NAME, "updateWebhook", e.getStatus().getCode().name(),
                    "Failed to update webhook: " + e.getStatus().getDescription());
        }
    }

    /**
     * Delete a webhook.
     */
    public DeleteWebhookResponse deleteWebhook(long userId, String webhookId) {
        try {
            DeleteWebhookResponse response = stub.deleteWebhook(
                    DeleteWebhookRequest.newBuilder().setUserId(userId).setWebhookId(nullToEmpty(webhookId)).build());

            if (!response.getSuccess()) {
                throw new GrpcException(SERVICE_NAME, "deleteWebhook", "FAILED", response.getMessage());
            }
            return response;
        } catch (StatusRuntimeException e) {
            log.error("gRPC error in deleteWebhook: {}", e.getStatus());
            throw new GrpcException(SERVICE_NAME, "deleteWebhook", e.getStatus().getCode().name(),
                    "Failed to delete webhook: " + e.getStatus().getDescription());
        }
    }

    /**
     * Test a webhook by sending a test event.
     */
    public TestWebhookResponse testWebhook(long userId, String webhookId) {
        try {
            TestWebhookResponse response = stub.testWebhook(
                    TestWebhookRequest.newBuilder().setUserId(userId).setWebhookId(nullToEmpty(webhookId)).build());

            if (!response.getSuccess()) {
                throw new GrpcException(SERVICE_NAME, "testWebhook", "FAILED", response.getMessage());
            }
            return response;
        } catch (StatusRuntimeException e) {
            log.error("gRPC error in testWebhook: {}", e.getStatus());
            throw new GrpcException(SERVICE_NAME, "testWebhook", e.getStatus().getCode().name(),
                    "Failed to test webhook: " + e.getStatus().getDescription());
        }
    }

    private String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
