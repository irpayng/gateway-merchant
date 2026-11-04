package com.tms.gateway.modules.customer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record UpdateCustomerBlacklistRequest(@JsonProperty("blacklisted") @NotNull Boolean blacklisted) {
}
