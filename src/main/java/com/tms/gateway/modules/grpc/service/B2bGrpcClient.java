package com.tms.gateway.modules.grpc.service;

import com.tms.gateway.core.exception.GrpcException;
import com.tms.gateway.modules.grpc.b2b.B2bCommandResponse;
import com.tms.gateway.modules.grpc.b2b.BusinessServiceGrpc;
import com.tms.gateway.modules.grpc.b2b.CreateApiKeyRequest;
import com.tms.gateway.modules.grpc.b2b.CreateApiKeyResponse;
import com.tms.gateway.modules.grpc.b2b.EnableApiAccessRequest;
import com.tms.gateway.modules.grpc.b2b.EnableApiAccessResponse;
import com.tms.gateway.modules.grpc.b2b.GetApiKeysRequest;
import com.tms.gateway.modules.grpc.b2b.GetApiKeysResponse;
import com.tms.gateway.modules.grpc.b2b.RevokeApiKeyRequest;
import com.tms.gateway.modules.grpc.b2b.RotateApiKeyRequest;
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
     * Create a new API key for a pg_merchant user.
     */
    public CreateApiKeyResponse createApiKey(long userId, String environment) {
        try {
            CreateApiKeyResponse response = stub.createApiKey(CreateApiKeyRequest.newBuilder().setUserId(userId)
                    .setEnvironment(nullToEmpty(environment)).build());

            if (!response.getSuccess()) {
                throw new GrpcException(SERVICE_NAME, "createApiKey", "FAILED", response.getMessage());
            }
            return response;
        } catch (StatusRuntimeException e) {
            log.error("gRPC error in createApiKey: {}", e.getStatus());
            throw new GrpcException(SERVICE_NAME, "createApiKey", e.getStatus().getCode().name(),
                    "Failed to create API key: " + e.getStatus().getDescription());
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

    private String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
