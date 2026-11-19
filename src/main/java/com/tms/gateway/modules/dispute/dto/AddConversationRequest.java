package com.tms.gateway.modules.dispute.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddConversationRequest {

    @NotBlank(message = "The message field is required.")
    private String message;
}
