package com.tms.gateway.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for the forgot-password endpoint.
 */
@Data
public class ForgotPasswordRequest {

    /**
     * The email address associated with the account.
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
}
