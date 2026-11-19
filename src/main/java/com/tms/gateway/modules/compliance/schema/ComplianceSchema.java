package com.tms.gateway.modules.compliance.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Complete compliance schema for a business type.
 */
public record ComplianceSchema(@JsonProperty("business_type") String businessType, @JsonProperty("label") String label,
        @JsonProperty("description") String description, @JsonProperty("steps") List<StepSchema> steps) {

    public static Builder builder(BusinessType type, String label) {
        return new Builder(type, label);
    }

    public static class Builder {
        private final BusinessType type;
        private final String label;
        private String description;
        private List<StepSchema> steps;

        private Builder(BusinessType type, String label) {
            this.type = type;
            this.label = label;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder steps(List<StepSchema> steps) {
            this.steps = steps;
            return this;
        }

        public Builder steps(StepSchema... steps) {
            this.steps = List.of(steps);
            return this;
        }

        public ComplianceSchema build() {
            return new ComplianceSchema(type.getValue(), label, description, steps);
        }
    }
}
