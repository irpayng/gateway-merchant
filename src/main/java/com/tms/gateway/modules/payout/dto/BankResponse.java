package com.tms.gateway.modules.payout.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Bank code information for name enquiry dropdown.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BankResponse(@JsonProperty("code") String code, @JsonProperty("name") String name,
        @JsonProperty("nip_code") String nipCode, @JsonProperty("logo_url") String logoUrl) {
}
