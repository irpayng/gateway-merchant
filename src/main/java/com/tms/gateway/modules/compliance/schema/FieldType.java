package com.tms.gateway.modules.compliance.schema;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Field input types for compliance schema.
 */
public enum FieldType {
    TEXT("text"), EMAIL("email"), PHONE("phone"), SELECT("select"), DATE("date"), NUMBER("number"), CURRENCY(
            "currency"), CHECKBOX("checkbox"), FILE("file"), TEXTAREA("textarea");

    private final String value;

    FieldType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
