package com.tms.gateway.modules.compliance.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
/**
 * Nullable fields support partial drafts. Omitted scalar values retain their
 * saved value.
 */
public record SaveOwnerRequest(@JsonProperty("id") Long id, @JsonProperty("role") String role,
        @JsonProperty("date_of_birth") String dateOfBirth, @JsonProperty("address") String address,
        @JsonProperty("id_document_type") String idDocumentType, @JsonProperty("full_name") String fullName,
        @JsonProperty("nationality") String nationality, @JsonProperty("state") String state,
        @JsonProperty("lga") String lga, @JsonProperty("same_as_business_address") Boolean sameAsBusinessAddress) {
}
