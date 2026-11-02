package com.tms.gateway.modules.compliance.schema;

/**
 * Supported business types for compliance flows.
 */
public enum BusinessType {
    STARTER("starter"), REGISTERED("registered");

    private final String value;

    BusinessType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static BusinessType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (BusinessType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown business type: " + value);
    }
}
