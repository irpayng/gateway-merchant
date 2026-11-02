package com.tms.gateway.modules.grpc.service;

import com.config.grpc.BankCodeInfo;
import com.config.grpc.ConfigServiceGrpc;
import com.config.grpc.ListBankCodesRequest;
import com.config.grpc.ListBankCodesResponse;
import com.config.grpc.ListMostUsedBanksRequest;
import com.config.grpc.ListMostUsedBanksResponse;
import com.tms.gateway.core.exception.AppException;
import com.tms.gateway.modules.grpc.config.GrpcProperties;
import com.tms.gateway.modules.payout.dto.BankResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * gRPC client for tms-config service. Used to fetch reference data like bank
 * codes.
 */
@Slf4j
@Service
public class ConfigGrpcClient {

    private final GrpcProperties properties;

    private ManagedChannel channel;
    private ConfigServiceGrpc.ConfigServiceBlockingStub stub;

    @Value("${grpc.api.key}")
    private String apiKey;

    public ConfigGrpcClient(GrpcProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        channel = ManagedChannelBuilder.forAddress(properties.getConfigServiceHost(), properties.getConfigServicePort())
                .usePlaintext().build();

        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER), apiKey);

        stub = ConfigServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));

        log.info("ConfigGrpcClient initialized: {}:{}", properties.getConfigServiceHost(),
                properties.getConfigServicePort());
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) {
            try {
                if (!channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)) {
                    channel.shutdownNow();
                }
            } catch (InterruptedException e) {
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private ConfigServiceGrpc.ConfigServiceBlockingStub timed() {
        return stub.withDeadlineAfter(properties.getTimeout(), TimeUnit.SECONDS);
    }

    private <T> T invoke(String operation, Supplier<T> call) {
        try {
            return call.get();
        } catch (StatusRuntimeException e) {
            HttpStatus http = switch (e.getStatus().getCode()) {
                case UNAVAILABLE, UNIMPLEMENTED -> HttpStatus.SERVICE_UNAVAILABLE;
                case DEADLINE_EXCEEDED -> HttpStatus.GATEWAY_TIMEOUT;
                default -> HttpStatus.BAD_GATEWAY;
            };
            log.error("Config {} failed: {} - {}", operation, e.getStatus().getCode(), e.getStatus().getDescription());
            throw new AppException("Config service unavailable: " + operation, http);
        }
    }

    /**
     * Get all bank codes from tms-config.
     */
    @Cacheable(value = "banks", unless = "#result == null || #result.isEmpty()")
    public List<BankResponse> listBankCodes() {
        log.debug("Fetching bank codes from config service via gRPC");
        ListBankCodesResponse response = invoke("listBankCodes",
                () -> timed().listBankCodes(ListBankCodesRequest.getDefaultInstance()));
        return response.getBanksList().stream().map(this::toBankResponse).toList();
    }

    /**
     * Get most commonly used banks from tms-config.
     */
    @Cacheable(value = "banks-most-used", unless = "#result == null || #result.isEmpty()")
    public List<BankResponse> listMostUsedBanks() {
        log.debug("Fetching most used banks from config service via gRPC");
        ListMostUsedBanksResponse response = invoke("listMostUsedBanks",
                () -> timed().listMostUsedBanks(ListMostUsedBanksRequest.getDefaultInstance()));
        return response.getBanksList().stream().map(this::toBankResponse).toList();
    }

    private BankResponse toBankResponse(BankCodeInfo info) {
        return new BankResponse(info.getCode(), info.getName(), info.getNipCode().isBlank() ? null : info.getNipCode(),
                info.getLogoUrl().isBlank() ? null : info.getLogoUrl());
    }
}
