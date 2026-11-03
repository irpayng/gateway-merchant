package com.tms.gateway.modules.compliance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import java.util.List;

public record RegisteredBusinessDetailsRequest(@JsonProperty("country") String country,
        @JsonProperty("industry") String industry, @JsonProperty("category") String category,
        @JsonProperty("trading_name") String tradingName, @JsonProperty("sales_volume") String salesVolume,
        @JsonProperty("business_description") String businessDescription,
        @JsonProperty("business_type") String businessType, @JsonProperty("support_email") @Email String supportEmail,
        @JsonProperty("dispute_email") @Email String disputeEmail,
        @JsonProperty("same_as_support_email") Boolean sameAsSupportEmail, @JsonProperty("phone") String phone,
        @JsonProperty("address") String address, @JsonProperty("state") String state, @JsonProperty("lga") String lga,
        @JsonProperty("business_address_document_id") String businessAddressDocumentId,
        @JsonProperty("online_links") @Valid List<OnlineLink> onlineLinks) {
}
