package com.tms.gateway.modules.compliance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Request to save step 1: Profile details.
 */
public record SaveProfileRequest(@NotBlank(message = "Country is required") String country,

        @NotBlank(message = "Industry is required") String industry,

        @NotBlank(message = "Category is required") String category,

        @JsonProperty("trading_name") @NotBlank(message = "Trading name is required") String tradingName,

        @JsonProperty("sales_volume") @NotBlank(message = "Sales volume is required") String salesVolume,

        @JsonProperty("business_type") @NotBlank(message = "Business type is required") String businessType,

        @JsonProperty("business_name") String businessName,

        @JsonProperty("rc_number") String rcNumber,

        @JsonProperty("cac_url") String cacUrl) {
}
