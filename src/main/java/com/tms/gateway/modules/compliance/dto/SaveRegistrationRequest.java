package com.tms.gateway.modules.compliance.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
/**
 * Nullable fields support partial drafts. Omitted scalar values retain their
 * saved value.
 */
public record SaveRegistrationRequest(@JsonProperty("registration_number") String registrationNumber,
        @JsonProperty("tax_identification_number") String taxIdentificationNumber) {
}
