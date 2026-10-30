package com.tms.gateway.modules.auth.controller;

import com.tms.gateway.core.dto.ApiResponse;
import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.modules.auth.dto.LoginRequest;
import com.tms.gateway.modules.auth.dto.LoginResponse;
import com.tms.gateway.modules.auth.dto.RegisterRequest;
import com.tms.gateway.modules.auth.dto.RegisterResponse;
import com.tms.gateway.modules.auth.dto.ResendOtpRequest;
import com.tms.gateway.modules.auth.dto.UserData;
import com.tms.gateway.modules.auth.dto.VerifyEmailRequest;
import com.tms.gateway.modules.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints for pg_merchant dashboard.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Login with email and password.
     *
     * <p>
     * Uses the same credentials from pg_merchant registration in tms-user. Returns
     * a JWT token for subsequent authenticated requests.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    /**
     * Register a new pg_merchant account.
     *
     * <p>
     * Creates a new user with type "pg_merchant" and a unique MID. Sends an email
     * verification OTP that must be confirmed before login is allowed.
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.ok(ApiResponse.success(response, response.getMessage()));
    }

    /**
     * Verify email address using the OTP sent during registration.
     *
     * <p>
     * Once verified, the merchant can log in to the dashboard.
     */
    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Email verified successfully"));
    }

    /**
     * Resend the email verification OTP.
     *
     * <p>
     * Throttled to one request per 60 seconds.
     */
    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<Void>> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        authService.resendOtp(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Verification code sent"));
    }

    /**
     * Get current authenticated user's profile and compliance status.
     *
     * <p>
     * Data is read from the local replica for fast response. Includes
     * compliance_status and compliance_required to help the frontend determine what
     * screens to show.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserData>> me(@AuthenticationPrincipal GatewayMerchantUserDetails userDetails) {
        UserData userData = authService.getCurrentUser(userDetails);
        return ResponseEntity.ok(ApiResponse.success(userData));
    }
}
