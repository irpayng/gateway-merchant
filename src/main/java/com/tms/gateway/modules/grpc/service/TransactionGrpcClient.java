package com.tms.gateway.modules.grpc.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tms.gateway.core.exception.AppException;
import com.tms.gateway.grpc.transaction.GetRefundRequest;
import com.tms.gateway.grpc.transaction.ListRefundsRequest;
import com.tms.gateway.grpc.transaction.ListRefundsResponse;
import com.tms.gateway.grpc.transaction.NameEnquiryGrpcRequest;
import com.tms.gateway.grpc.transaction.Refund;
import com.tms.gateway.grpc.transaction.TransactionCommandResponse;
import com.tms.gateway.grpc.transaction.TransactionServiceGrpc;
import com.tms.gateway.modules.grpc.config.GrpcProperties;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * gRPC client for transaction-service. Used for name enquiry (bank account
 * verification) before merchant payouts.
 */
@Slf4j
@Service
public class TransactionGrpcClient {

    private final GrpcProperties properties;
    private final ObjectMapper objectMapper;

    private ManagedChannel channel;
    private TransactionServiceGrpc.TransactionServiceBlockingStub stub;

    @Value("${grpc.api.key}")
    private String apiKey;

    public TransactionGrpcClient(GrpcProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        channel = ManagedChannelBuilder
                .forAddress(properties.getTransactionServiceHost(), properties.getTransactionServicePort())
                .usePlaintext().build();

        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER), apiKey);

        stub = TransactionServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));

        log.info("TransactionGrpcClient initialized: {}:{}", properties.getTransactionServiceHost(),
                properties.getTransactionServicePort());
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

    private TransactionServiceGrpc.TransactionServiceBlockingStub timed() {
        return stub.withDeadlineAfter(properties.getTimeout(), TimeUnit.SECONDS);
    }

    private <T> T invoke(String operation, Supplier<T> call) {
        try {
            return call.get();
        } catch (StatusRuntimeException e) {
            HttpStatus http = switch (e.getStatus().getCode()) {
                case INVALID_ARGUMENT -> HttpStatus.UNPROCESSABLE_ENTITY;
                case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
                case NOT_FOUND -> HttpStatus.NOT_FOUND;
                case ALREADY_EXISTS, ABORTED, FAILED_PRECONDITION -> HttpStatus.CONFLICT;
                case RESOURCE_EXHAUSTED -> HttpStatus.TOO_MANY_REQUESTS;
                case UNAVAILABLE, UNIMPLEMENTED -> HttpStatus.SERVICE_UNAVAILABLE;
                case DEADLINE_EXCEEDED -> HttpStatus.GATEWAY_TIMEOUT;
                default -> HttpStatus.BAD_GATEWAY;
            };
            log.error("Transaction {} failed: {} - {}", operation, e.getStatus().getCode(),
                    e.getStatus().getDescription());
            throw new AppException(
                    "Transaction " + operation + " failed: "
                            + (e.getStatus().getDescription() != null ? e.getStatus().getDescription() : "unavailable"),
                    http);
        }
    }

    /**
     * Verify a bank account holder's name via NIP/NIBSS.
     *
     * @param accountNumber
     *            10-digit bank account number
     * @param bankCode
     *            CBN bank code (e.g., "000013" for GTBank)
     * @return Map with account_name, account_number, bank_code
     * @throws AppException
     *             if the enquiry fails or account not found
     */
    public Map<String, Object> nameEnquiry(String accountNumber, String bankCode) {
        log.info("Name enquiry: account={} bank={}", accountNumber, bankCode);

        TransactionCommandResponse response = invoke("nameEnquiry", () -> timed().nameEnquiry(
                NameEnquiryGrpcRequest.newBuilder().setAccountNumber(accountNumber).setBankCode(bankCode).build()));

        if (!response.getSuccess()) {
            String message = response.getMessage().isBlank() ? "Name enquiry failed" : response.getMessage();
            log.warn("Name enquiry failed: account={} bank={} message={}", accountNumber, bankCode, message);
            throw new AppException(message, HttpStatus.UNPROCESSABLE_ENTITY);
        }

        try {
            if (!response.getDataJson().isBlank()) {
                return objectMapper.readValue(response.getDataJson(), new TypeReference<Map<String, Object>>() {
                });
            }
        } catch (Exception e) {
            log.error("Failed to parse name enquiry response: {}", e.getMessage());
        }

        throw new AppException("Name enquiry returned no data", HttpStatus.BAD_GATEWAY);
    }

    public ListRefundsResponse listRefunds(ListRefundsRequest request) {
        return invoke("listRefunds", () -> timed().listRefunds(request));
    }

    public Refund getRefund(GetRefundRequest request) {
        return invoke("getRefund", () -> timed().getRefund(request));
    }
}
