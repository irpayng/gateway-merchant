package com.tms.gateway.modules.compliance.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
/**
 * Nullable fields support partial drafts. Omitted scalar values retain their
 * saved value.
 */
public record OnlineLink(@JsonProperty("platform") String platform, @JsonProperty("url") String url) {
}
