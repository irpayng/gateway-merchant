package com.tms.gateway.modules.payout.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Bank code information for name enquiry dropdown.
 */
public record BankResponse(@JsonProperty("code") String code,

        @JsonProperty("name") String name,

        @JsonProperty("category") Integer category) {

    /**
     * Create from config service response map.
     */
    public static BankResponse fromMap(Map<String, Object> data) {
        String code = data.get("code") != null ? data.get("code").toString() : null;
        String name = data.get("name") != null ? data.get("name").toString() : null;
        Integer category = null;
        if (data.get("category") != null) {
            try {
                category = Integer.parseInt(data.get("category").toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return new BankResponse(code, name, category);
    }
}
