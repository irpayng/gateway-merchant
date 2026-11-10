package com.tms.gateway.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for the reset-password endpoint.
 */
@Data
public class ResetPasswordRequest {

    /**
     * The email or phone number used during the forgot-password step.
     */
    @NotBlank(message = "Email is required")
    private String email;

    /**
     * The OTP/token sent via email during forgot-password.
     */
    @NotBlank(message = "Token is required")
    private String token;

    /**
     * The new password to set.
     */
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
}
