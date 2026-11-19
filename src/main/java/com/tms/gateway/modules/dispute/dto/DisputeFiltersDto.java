package com.tms.gateway.modules.dispute.dto;

import java.util.List;

/**
 * Static filter options for the disputes list endpoint.
 */
public record DisputeFiltersDto(List<FilterOption> statuses) {

    public record FilterOption(String code, String label) {
    }
}
