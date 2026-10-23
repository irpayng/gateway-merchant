package com.tms.gateway.modules.compliance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Request to save a single owner (step 3).
 */
public record SaveOwnerRequest(Long id,

        String role,

        @JsonProperty("first_name") @NotBlank(message = "First name is required") String firstName,

        @JsonProperty("last_name") @NotBlank(message = "Last name is required") String lastName,

        String email,

        String phone,

        String bvn,

        String nin,

        @JsonProperty("date_of_birth") String dateOfBirth,

        String address,

        @JsonProperty("ownership_percentage") Integer ownershipPercentage,

        @JsonProperty("id_document_url") String idDocumentUrl,

        @JsonProperty("id_document_type") String idDocumentType) {
}
