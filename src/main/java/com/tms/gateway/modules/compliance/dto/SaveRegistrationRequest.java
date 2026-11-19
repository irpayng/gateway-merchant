package com.tms.gateway.modules.compliance.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
/**
 * Registration details for registered businesses.
 *
 * <p>
 * Nullable fields support partial drafts. Omitted scalar values retain their
 * saved value.
 */
@Schema(description = "Registration details request")
public record SaveRegistrationRequest(@Schema(hidden = true) @JsonProperty("business_type") String businessType,
        @Schema(description = "CAC registration number (RC number)", example = "RC1234567") @JsonProperty("registration_number") String registrationNumber,
        @Schema(description = "Tax Identification Number from FIRS", example = "12345678-0001") @JsonProperty("tax_identification_number") String taxIdentificationNumber) {
}
