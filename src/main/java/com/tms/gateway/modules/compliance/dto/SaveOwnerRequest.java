package com.tms.gateway.modules.compliance.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
/**
 * Nullable fields support partial drafts. Omitted scalar values retain their
 * saved value.
 */
public record SaveOwnerRequest(@JsonProperty("id") Long id, @JsonProperty("role") String role,
        @JsonProperty("first_name") String firstName, @JsonProperty("last_name") String lastName,
        @JsonProperty("email") @Email String email, @JsonProperty("phone") String phone,
        @JsonProperty("bvn") String bvn, @JsonProperty("nin") String nin,
        @JsonProperty("date_of_birth") String dateOfBirth, @JsonProperty("address") String address,
        @JsonProperty("ownership_percentage") Integer ownershipPercentage,
        @JsonProperty("id_document_url") String idDocumentUrl, @JsonProperty("id_document_type") String idDocumentType,
        @JsonProperty("full_name") String fullName, @JsonProperty("nationality") String nationality,
        @JsonProperty("state") String state, @JsonProperty("lga") String lga,
        @JsonProperty("same_as_business_address") Boolean sameAsBusinessAddress,
        @JsonProperty("id_document_id") String idDocumentId,
        @JsonProperty("proof_of_address_document_id") String proofOfAddressDocumentId) {
}
