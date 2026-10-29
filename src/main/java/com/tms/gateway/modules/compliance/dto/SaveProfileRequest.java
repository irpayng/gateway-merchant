package com.tms.gateway.modules.compliance.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
/**
 * Nullable fields support partial drafts. Omitted scalar values retain their
 * saved value.
 */
public record SaveProfileRequest(@JsonProperty("country") String country, @JsonProperty("industry") String industry,
        @JsonProperty("category") String category, @JsonProperty("trading_name") String tradingName,
        @JsonProperty("sales_volume") String salesVolume, @JsonProperty("business_type") String businessType,
        @JsonProperty("business_name") String businessName, @JsonProperty("rc_number") String rcNumber,
        @JsonProperty("cac_url") String cacUrl, @JsonProperty("business_description") String businessDescription,
        @JsonProperty("currency") String currency) {
}
