package com.tms.gateway.modules.compliance.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import java.util.List;
/**
 * Nullable fields support partial drafts. Omitted scalar values retain their
 * saved value.
 */
public record SaveContactRequest(@JsonProperty("email") @Email String email, @JsonProperty("phone") String phone,
        @JsonProperty("address") String address, @JsonProperty("support_email") @Email String supportEmail,
        @JsonProperty("dispute_email") @Email String disputeEmail, @JsonProperty("lga") String lga,
        @JsonProperty("same_as_support_email") Boolean sameAsSupportEmail,
        @JsonProperty("business_address_document_id") String businessAddressDocumentId,
        @JsonProperty("online_links") @Valid List<OnlineLink> onlineLinks) {
}
