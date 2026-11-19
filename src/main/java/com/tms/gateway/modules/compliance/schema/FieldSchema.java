package com.tms.gateway.modules.compliance.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Schema definition for a single form field.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldSchema(@JsonProperty("name") String name, @JsonProperty("label") String label,
        @JsonProperty("type") FieldType type, @JsonProperty("required") boolean required,
        @JsonProperty("options_ref") String optionsRef, @JsonProperty("options") List<OptionValue> options,
        @JsonProperty("placeholder") String placeholder, @JsonProperty("hint") String hint,
        @JsonProperty("max_length") Integer maxLength, @JsonProperty("min_length") Integer minLength,
        @JsonProperty("pattern") String pattern, @JsonProperty("depends_on") DependsOn dependsOn) {

    public static Builder builder(String name, String label, FieldType type) {
        return new Builder(name, label, type);
    }

    public static class Builder {
        private final String name;
        private final String label;
        private final FieldType type;
        private boolean required = false;
        private String optionsRef;
        private List<OptionValue> options;
        private String placeholder;
        private String hint;
        private Integer maxLength;
        private Integer minLength;
        private String pattern;
        private DependsOn dependsOn;

        private Builder(String name, String label, FieldType type) {
            this.name = name;
            this.label = label;
            this.type = type;
        }

        public Builder required() {
            this.required = true;
            return this;
        }

        public Builder optionsRef(String ref) {
            this.optionsRef = ref;
            return this;
        }

        public Builder options(List<OptionValue> options) {
            this.options = options;
            return this;
        }

        public Builder placeholder(String placeholder) {
            this.placeholder = placeholder;
            return this;
        }

        public Builder hint(String hint) {
            this.hint = hint;
            return this;
        }

        public Builder maxLength(int maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        public Builder minLength(int minLength) {
            this.minLength = minLength;
            return this;
        }

        public Builder pattern(String pattern) {
            this.pattern = pattern;
            return this;
        }

        public Builder dependsOn(String field, Object value) {
            this.dependsOn = new DependsOn(field, value);
            return this;
        }

        public FieldSchema build() {
            return new FieldSchema(name, label, type, required, optionsRef, options, placeholder, hint, maxLength,
                    minLength, pattern, dependsOn);
        }
    }

    public record OptionValue(@JsonProperty("value") String value, @JsonProperty("label") String label) {
        public static OptionValue of(String value, String label) {
            return new OptionValue(value, label);
        }
    }

    public record DependsOn(@JsonProperty("field") String field, @JsonProperty("value") Object value) {
    }
}
