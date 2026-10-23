package com.tms.gateway.modules.compliance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Request to save step 3: Owners (replaces all existing owners).
 */
public record SaveOwnersRequest(
        @NotEmpty(message = "At least one owner is required") @Valid List<SaveOwnerRequest> owners) {
}
