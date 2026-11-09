package com.tms.gateway.modules.transaction.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Available filter and pagination options for transaction list.
 */
public record TransactionFiltersDto(@JsonProperty("statuses") List<FilterOption> statuses,
        @JsonProperty("payment_methods") List<FilterOption> paymentMethods,
        @JsonProperty("limits") List<Integer> limits) {

    public record FilterOption(@JsonProperty("value") String value, @JsonProperty("label") String label) {
    }
}
