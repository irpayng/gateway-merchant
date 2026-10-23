package com.tms.gateway.modules.grpc.service;

import com.tms.gateway.core.exception.AppException;
import com.tms.gateway.core.exception.GrpcException;
import com.tms.gateway.grpc.user.AuthPgMerchantRequest;
import com.tms.gateway.grpc.user.AuthPgMerchantResponse;
import com.tms.gateway.grpc.user.UserServiceGrpc;
import com.tms.gateway.modules.grpc.config.GrpcProperties;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * gRPC client for tms-user service.
 *
 * <p>
 * Provides methods for authenticating pg_merchant users using their
 * registration credentials.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserGrpcClient {

    private final UserServiceGrpc.UserServiceBlockingStub userStub;
    private final GrpcProperties props;

    /**
     * Authenticate a pg_merchant user by email and password.
     *
     * @param email
     *            the user's email address
     * @param password
     *            the user's password
     * @return the authentication response with user details on success
     * @throws AppException
     *             if authentication fails (invalid credentials, blocked, etc.)
     * @throws GrpcException
     *             if the gRPC call fails
     */
    public AuthPgMerchantResponse authPgMerchant(String email, String password) {
        try {
            AuthPgMerchantRequest request = AuthPgMerchantRequest.newBuilder().setEmail(email).setPassword(password)
                    .build();

            AuthPgMerchantResponse response = userStub.withDeadlineAfter(props.getTimeout(), TimeUnit.SECONDS)
                    .authPgMerchant(request);

            if (!response.getSuccess()) {
                // Translate gRPC error reasons to HTTP exceptions
                HttpStatus status = switch (response.getReason()) {
                    case "invalid_credentials" -> HttpStatus.UNAUTHORIZED;
                    case "blocked", "suspended", "not_pg_merchant" -> HttpStatus.FORBIDDEN;
                    case "email_not_verified" -> HttpStatus.FORBIDDEN;
                    default -> HttpStatus.BAD_REQUEST;
                };
                throw new AppException(response.getMessage(), status);
            }

            return response;

        } catch (StatusRuntimeException e) {
            log.error("gRPC call to user-service failed: {}", e.getStatus());
            throw new GrpcException("user-service", "AuthPgMerchant", e.getStatus().getCode().name(),
                    "Failed to authenticate: " + e.getStatus().getDescription());
        }
    }
}
