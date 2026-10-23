package com.tms.gateway.modules.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User data returned in login response and /auth/me endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserData {

    private Long id;

    @JsonProperty("merchant_id")
    private String merchantId;

    private String email;

    @JsonProperty("phone_number")
    private String phoneNumber;

    @JsonProperty("business_name")
    private String businessName;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    @JsonProperty("display_name")
    private String displayName;

    /**
     * Compliance status: none, draft, submitted, under_review, approved, rejected
     */
    @JsonProperty("compliance_status")
    @Builder.Default
    private String complianceStatus = "none";

    /**
     * Current compliance step (0 if none, 1-5 for progress)
     */
    @JsonProperty("compliance_step")
    @Builder.Default
    private int complianceStep = 0;

    /**
     * True if the user must complete compliance before accessing other features.
     */
    @JsonProperty("compliance_required")
    @Builder.Default
    private boolean complianceRequired = true;
}
