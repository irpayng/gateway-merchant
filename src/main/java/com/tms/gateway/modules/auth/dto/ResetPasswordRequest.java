package com.tms.gateway.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for the reset-password endpoint.
 */
@Data
public class ResetPasswordRequest {

    /**
     * The email address associated with the account.
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    /**
     * The OTP code sent via email during forgot-password.
     */
    @NotBlank(message = "Verification code is required")
    private String otp;

    /**
     * The new password to set.
     */
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
}
