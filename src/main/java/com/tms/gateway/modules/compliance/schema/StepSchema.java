package com.tms.gateway.modules.compliance.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Schema definition for a compliance step.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StepSchema(@JsonProperty("key") String key, @JsonProperty("label") String label,
        @JsonProperty("description") String description, @JsonProperty("fields") List<FieldSchema> fields,
        @JsonProperty("multiple") Boolean multiple, @JsonProperty("min_items") Integer minItems,
        @JsonProperty("max_items") Integer maxItems, @JsonProperty("add_label") String addLabel,
        @JsonProperty("documents_hint") String documentsHint) {

    public static Builder builder(String key, String label) {
        return new Builder(key, label);
    }

    public static class Builder {
        private final String key;
        private final String label;
        private String description;
        private List<FieldSchema> fields;
        private Boolean multiple;
        private Integer minItems;
        private Integer maxItems;
        private String addLabel;
        private String documentsHint;

        private Builder(String key, String label) {
            this.key = key;
            this.label = label;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder fields(List<FieldSchema> fields) {
            this.fields = fields;
            return this;
        }

        public Builder fields(FieldSchema... fields) {
            this.fields = List.of(fields);
            return this;
        }

        public Builder multiple(int min, int max, String addLabel) {
            this.multiple = true;
            this.minItems = min;
            this.maxItems = max;
            this.addLabel = addLabel;
            return this;
        }

        public Builder documentsHint(String hint) {
            this.documentsHint = hint;
            return this;
        }

        public StepSchema build() {
            return new StepSchema(key, label, description, fields, multiple, minItems, maxItems, addLabel,
                    documentsHint);
        }
    }
}
