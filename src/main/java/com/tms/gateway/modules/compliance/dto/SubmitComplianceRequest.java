package com.tms.gateway.modules.compliance.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
public record SubmitComplianceRequest(
        @JsonProperty("agreement_accepted") @NotNull @AssertTrue(message = "Agreement must be accepted") Boolean agreementAccepted) {
}
