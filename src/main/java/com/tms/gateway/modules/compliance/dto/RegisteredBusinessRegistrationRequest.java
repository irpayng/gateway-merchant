package com.tms.gateway.modules.compliance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RegisteredBusinessRegistrationRequest(@JsonProperty("registration_number") String registrationNumber,
        @JsonProperty("tax_identification_number") String taxIdentificationNumber) {
}
