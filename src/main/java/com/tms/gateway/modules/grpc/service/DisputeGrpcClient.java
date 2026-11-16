package com.tms.gateway.modules.grpc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tms.gateway.core.exception.AppException;
import com.tms.gateway.modules.grpc.config.GrpcProperties;
import com.tms.gateway.modules.grpc.dispute.*;
import io.grpc.*;
import io.grpc.stub.MetadataUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * gRPC client for dispute service operations. Handles creating disputes and
 * adding user conversations.
 */
@Service
public class DisputeGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(DisputeGrpcClient.class);

    @Value("${grpc.debug-errors:false}")
    private boolean debugErrors;

    @Value("${grpc.api.key}")
    private String apiKey;

    private final GrpcProperties properties;
    private final ObjectMapper objectMapper;

    private ManagedChannel channel;
    private DisputeServiceGrpc.DisputeServiceBlockingStub stub;

    public DisputeGrpcClient(GrpcProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        channel = ManagedChannelBuilder
                .forAddress(properties.getDisputeServiceHost(), properties.getDisputeServicePort()).usePlaintext()
                .build();

        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER), apiKey);

        stub = DisputeServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
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

    private DisputeServiceGrpc.DisputeServiceBlockingStub timed() {
        return stub.withDeadlineAfter(properties.getTimeout(), TimeUnit.SECONDS);
    }

    private <T> T invoke(String operation, Supplier<T> call) {
        long started = System.nanoTime();
        log.debug("Dispute gRPC request operation={} target={}:{}", operation, properties.getDisputeServiceHost(),
                properties.getDisputeServicePort());
        try {
            return call.get();
        } catch (StatusRuntimeException e) {
            log.error("Dispute gRPC failed operation={} target={}:{} status={} description={} elapsed_ms={}", operation,
                    properties.getDisputeServiceHost(), properties.getDisputeServicePort(), e.getStatus().getCode(),
                    e.getStatus().getDescription(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), e);

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

            String message = "Dispute " + operation + " failed (" + e.getStatus().getCode().name() + ")";
            if (debugErrors && e.getStatus().getDescription() != null) {
                message += ": " + e.getStatus().getDescription();
            }

            var failure = new AppException(message, http);
            failure.initCause(e);
            throw failure;
        }
    }

    /**
     * Create a new dispute for a merchant.
     *
     * @param userId
     *            the merchant's user ID
     * @param transactionReference
     *            optional transaction reference
     * @param subject
     *            dispute subject/reason
     * @param message
     *            optional initial message
     * @param actorName
     *            display name of the merchant
     * @param actorEmail
     *            email of the merchant
     * @return result map with success, message, and data
     */
    public Map<String, Object> createDispute(Long userId, String transactionReference, String subject, String message,
            String actorName, String actorEmail) {
        return invoke("CreateDispute", () -> {
            var actor = Actor.newBuilder().setId(String.valueOf(userId)).setName(actorName != null ? actorName : "")
                    .setEmail(actorEmail != null ? actorEmail : "").build();

            var builder = com.tms.gateway.modules.grpc.dispute.CreateDisputeRequest.newBuilder().setUserId(userId)
                    .setSubject(subject).setActor(actor);

            if (transactionReference != null && !transactionReference.isBlank()) {
                builder.setTransactionReference(transactionReference);
            }
            if (message != null && !message.isBlank()) {
                builder.setMessage(message);
            }

            DisputeCommandResponse resp = timed().createDispute(builder.build());
            return toMap(resp);
        });
    }

    /**
     * Add a user/merchant message to a dispute conversation.
     *
     * @param disputeId
     *            the dispute ID
     * @param userId
     *            the merchant's user ID
     * @param message
     *            the message content
     * @param senderName
     *            optional display name
     * @return result map with success, message, and data
     */
    public Map<String, Object> addUserConversation(Long disputeId, Long userId, String message, String senderName) {
        return invoke("AddUserConversation", () -> {
            var builder = AddUserConversationRequest.newBuilder().setDisputeId(disputeId).setUserId(userId)
                    .setMessage(message);

            if (senderName != null && !senderName.isBlank()) {
                builder.setSenderName(senderName);
            }

            DisputeCommandResponse resp = timed().addUserConversation(builder.build());
            return toMap(resp);
        });
    }

    /**
     * Close a dispute (merchant-initiated).
     *
     * @param disputeId
     *            the dispute ID
     * @param userId
     *            the merchant's user ID
     * @param actorName
     *            display name of the merchant
     * @param actorEmail
     *            email of the merchant
     * @return result map with success and message
     */
    public Map<String, Object> closeDispute(Long disputeId, Long userId, String actorName, String actorEmail) {
        return invoke("CloseDispute", () -> {
            var actor = Actor.newBuilder().setId(String.valueOf(userId)).setName(actorName != null ? actorName : "")
                    .setEmail(actorEmail != null ? actorEmail : "").build();

            var request = CloseDisputeRequest.newBuilder().setDisputeId(disputeId).setActor(actor).build();

            DisputeCommandResponse resp = timed().closeDispute(request);
            return toMap(resp);
        });
    }

    private Map<String, Object> toMap(DisputeCommandResponse resp) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", resp.getSuccess());
        result.put("message", resp.getMessage());
        if (!resp.getReference().isBlank()) {
            result.put("reference", resp.getReference());
        }
        if (!resp.getDataJson().isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = objectMapper.readValue(resp.getDataJson(), Map.class);
                result.putAll(data);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse data_json from dispute response", e);
            }
        }
        return result;
    }
}
