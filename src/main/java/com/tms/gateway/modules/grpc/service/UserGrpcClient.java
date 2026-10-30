package com.tms.gateway.modules.grpc.service;

import com.tms.gateway.core.exception.AppException;
import com.tms.gateway.core.exception.GrpcException;
import com.tms.gateway.grpc.user.AuthPgMerchantRequest;
import com.tms.gateway.grpc.user.AuthPgMerchantResponse;
import com.tms.gateway.grpc.user.RegisterPgMerchantRequest;
import com.tms.gateway.grpc.user.RegisterPgMerchantResponse;
import com.tms.gateway.grpc.user.ResendPgMerchantOtpRequest;
import com.tms.gateway.grpc.user.ResendPgMerchantOtpResponse;
import com.tms.gateway.grpc.user.UserServiceGrpc;
import com.tms.gateway.grpc.user.VerifyPgMerchantEmailRequest;
import com.tms.gateway.grpc.user.VerifyPgMerchantEmailResponse;
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
 * Provides methods for authenticating and registering pg_merchant users.
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

    /**
     * Register a new pg_merchant user.
     *
     * @param email
     *            the user's email address
     * @param password
     *            the user's password
     * @param phoneNumber
     *            the user's phone number
     * @param businessName
     *            the business name
     * @param businessType
     *            "starter" or "registered"
     * @param firstName
     *            the user's first name
     * @param lastName
     *            the user's last name
     * @param country
     *            ISO country code (defaults to "NG")
     * @return the registration response with user details
     * @throws AppException
     *             if registration fails (email/phone taken, validation error)
     * @throws GrpcException
     *             if the gRPC call fails
     */
    public RegisterPgMerchantResponse registerPgMerchant(String email, String password, String phoneNumber,
            String businessName, String businessType, String firstName, String lastName, String country) {
        try {
            RegisterPgMerchantRequest request = RegisterPgMerchantRequest.newBuilder().setEmail(email)
                    .setPassword(password).setPhoneNumber(phoneNumber).setBusinessName(businessName)
                    .setBusinessType(businessType).setFirstName(firstName).setLastName(lastName)
                    .setCountry(country != null ? country : "NG").build();

            RegisterPgMerchantResponse response = userStub.withDeadlineAfter(props.getTimeout(), TimeUnit.SECONDS)
                    .registerPgMerchant(request);

            if (!response.getSuccess()) {
                HttpStatus status = switch (response.getReason()) {
                    case "email_taken", "phone_taken" -> HttpStatus.CONFLICT;
                    case "validation_error" -> HttpStatus.UNPROCESSABLE_ENTITY;
                    default -> HttpStatus.BAD_REQUEST;
                };
                throw new AppException(response.getMessage(), status);
            }

            return response;

        } catch (StatusRuntimeException e) {
            log.error("gRPC call to user-service failed: {}", e.getStatus());
            throw new GrpcException("user-service", "RegisterPgMerchant", e.getStatus().getCode().name(),
                    "Failed to register: " + e.getStatus().getDescription());
        }
    }

    /**
     * Verify a pg_merchant's email address using the OTP.
     *
     * @param email
     *            the user's email address
     * @param otp
     *            the verification OTP
     * @throws AppException
     *             if verification fails (invalid/expired OTP)
     * @throws GrpcException
     *             if the gRPC call fails
     */
    public void verifyPgMerchantEmail(String email, String otp) {
        try {
            VerifyPgMerchantEmailRequest request = VerifyPgMerchantEmailRequest.newBuilder().setEmail(email).setOtp(otp)
                    .build();

            VerifyPgMerchantEmailResponse response = userStub.withDeadlineAfter(props.getTimeout(), TimeUnit.SECONDS)
                    .verifyPgMerchantEmail(request);

            if (!response.getSuccess()) {
                HttpStatus status = switch (response.getReason()) {
                    case "invalid_otp", "expired" -> HttpStatus.UNPROCESSABLE_ENTITY;
                    case "already_verified" -> HttpStatus.CONFLICT;
                    default -> HttpStatus.BAD_REQUEST;
                };
                throw new AppException(response.getMessage(), status);
            }

        } catch (StatusRuntimeException e) {
            log.error("gRPC call to user-service failed: {}", e.getStatus());
            throw new GrpcException("user-service", "VerifyPgMerchantEmail", e.getStatus().getCode().name(),
                    "Failed to verify email: " + e.getStatus().getDescription());
        }
    }

    /**
     * Resend the email verification OTP for a pg_merchant.
     *
     * @param email
     *            the user's email address
     * @throws AppException
     *             if resend fails (throttled, not found, already verified)
     * @throws GrpcException
     *             if the gRPC call fails
     */
    public void resendPgMerchantOtp(String email) {
        try {
            ResendPgMerchantOtpRequest request = ResendPgMerchantOtpRequest.newBuilder().setEmail(email).build();

            ResendPgMerchantOtpResponse response = userStub.withDeadlineAfter(props.getTimeout(), TimeUnit.SECONDS)
                    .resendPgMerchantOtp(request);

            if (!response.getSuccess()) {
                HttpStatus status = switch (response.getReason()) {
                    case "throttled" -> HttpStatus.TOO_MANY_REQUESTS;
                    case "not_found" -> HttpStatus.NOT_FOUND;
                    case "already_verified" -> HttpStatus.CONFLICT;
                    default -> HttpStatus.BAD_REQUEST;
                };
                throw new AppException(response.getMessage(), status);
            }

        } catch (StatusRuntimeException e) {
            log.error("gRPC call to user-service failed: {}", e.getStatus());
            throw new GrpcException("user-service", "ResendPgMerchantOtp", e.getStatus().getCode().name(),
                    "Failed to resend OTP: " + e.getStatus().getDescription());
        }
    }
}
