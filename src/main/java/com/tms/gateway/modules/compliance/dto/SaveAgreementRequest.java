package com.tms.gateway.modules.compliance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request to save step 5: Service agreement acceptance.
 */
public record SaveAgreementRequest(@JsonProperty("terms_accepted") boolean termsAccepted,

        @JsonProperty("terms_version") String termsVersion,

        @JsonProperty("privacy_accepted") boolean privacyAccepted,

        @JsonProperty("ip_address") String ipAddress,

        @JsonProperty("user_agent") String userAgent) {
}
