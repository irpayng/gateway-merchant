package com.tms.gateway.modules.compliance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SaveRegisteredShareholdersRequest(
        @JsonProperty("shareholders") @NotNull @Valid List<@NotNull RegisteredShareholderRequest> shareholders) {
}
