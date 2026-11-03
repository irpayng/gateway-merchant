package com.tms.gateway.modules.compliance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RegisteredShareholderRequest(@JsonProperty("full_name") String fullName,
        @JsonProperty("date_of_birth") String dateOfBirth, @JsonProperty("nationality") String nationality,
        @JsonProperty("role") String role, @JsonProperty("id_document_type") String idDocumentType,
        @JsonProperty("id_document_id") String idDocumentId, @JsonProperty("address") String address,
        @JsonProperty("same_as_business_address") Boolean sameAsBusinessAddress, @JsonProperty("lga") String lga,
        @JsonProperty("proof_of_address_document_id") String proofOfAddressDocumentId) {
}
