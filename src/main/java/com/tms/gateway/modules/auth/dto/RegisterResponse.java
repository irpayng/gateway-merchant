package com.tms.gateway.modules.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * Registration response for pg_merchant dashboard.
 */
@Data
@Builder
public class RegisterResponse {

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("merchant_id")
    private String merchantId;

    private String email;

    @JsonProperty("phone_number")
    private String phoneNumber;

    @JsonProperty("business_name")
    private String businessName;

    @JsonProperty("business_type")
    private String businessType;

    private String message;
}
