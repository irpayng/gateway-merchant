package com.tms.gateway.modules.compliance.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
/**
 * Registration details for registered businesses. Includes business_type to
 * distinguish between starter and registered business compliance flows.
 *
 * <p>
 * Nullable fields support partial drafts. Omitted scalar values retain their
 * saved value.
 */
public record SaveRegistrationRequest(@JsonProperty("business_type") String businessType,
        @JsonProperty("registration_number") String registrationNumber,
        @JsonProperty("tax_identification_number") String taxIdentificationNumber) {
}
