package com.tms.gateway.modules.dispute.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateDisputeRequest {

    @JsonProperty("transaction_reference")
    private String transactionReference;

    @NotBlank(message = "The subject field is required.")
    private String subject;

    private String message;
}
