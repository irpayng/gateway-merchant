package com.tms.gateway.modules.audit.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Turns request/response bodies into JSON that is safe to keep in the audit
 * log: secrets are masked, long strings (e.g. base64 blobs) are clipped, and
 * oversized payloads are replaced by a truncated preview.
 */
@Slf4j
@Component
public class AuditPayloadSanitizer {

    public static final String REDACTED = "[REDACTED]";

    static final int MAX_STRING_LENGTH = 1_000;
    static final int MAX_PAYLOAD_LENGTH = 32_000;
    private static final int PREVIEW_LENGTH = 2_000;

    /** Field names (normalised) masked when they contain any of these. */
    private static final List<String> SENSITIVE_FRAGMENTS = List.of("password", "secret", "token", "otp", "apikey",
            "privatekey", "authorization", "cvv", "cvc");

    /** Field names (normalised) masked only on an exact match. */
    private static final Set<String> SENSITIVE_EXACT = Set.of("pin", "transactionpin", "pan", "cardnumber");

    private final JsonMapper jsonMapper;

    public AuditPayloadSanitizer(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * Converts a body object into a redacted JSON tree. Strings holding JSON (such
     * as a multipart "data" part) are parsed so their fields are redacted too.
     * Returns {@code null} for a null body.
     */
    public JsonNode toTree(Object body) {
        if (body == null) {
            return null;
        }
        JsonNode tree;
        try {
            tree = body instanceof String text ? parseIfJson(text) : jsonMapper.valueToTree(body);
        } catch (RuntimeException e) {
            log.debug("Audit payload of type {} could not be serialised: {}", body.getClass().getName(),
                    e.getMessage());
            return JsonNodeFactory.instance.objectNode().put("_unserializable", body.getClass().getSimpleName());
        }
        return sanitize(tree);
    }

    /** Serialises an already-sanitised tree, truncating it when too large. */
    public String toJson(JsonNode tree) {
        if (tree == null || tree.isNull() || tree.isMissingNode()) {
            return null;
        }
        String json = jsonMapper.writeValueAsString(tree);
        if (json.length() <= MAX_PAYLOAD_LENGTH) {
            return json;
        }
        ObjectNode truncated = JsonNodeFactory.instance.objectNode();
        truncated.put("_truncated", true);
        truncated.put("_original_length", json.length());
        truncated.put("preview", json.substring(0, PREVIEW_LENGTH));
        return jsonMapper.writeValueAsString(truncated);
    }

    /** Parses stored JSON back into a plain Java structure for API responses. */
    public Object fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return jsonMapper.readValue(json, Object.class);
        } catch (RuntimeException e) {
            return json;
        }
    }

    /** Whether a field with this name must never be stored in clear text. */
    public static boolean isSensitiveField(String name) {
        if (name == null) {
            return false;
        }
        String normalised = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return SENSITIVE_EXACT.contains(normalised) || SENSITIVE_FRAGMENTS.stream().anyMatch(normalised::contains);
    }

    private JsonNode parseIfJson(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                return jsonMapper.readTree(trimmed);
            } catch (RuntimeException ignored) {
                // Not JSON after all; keep as text
            }
        }
        return JsonNodeFactory.instance.stringNode(text);
    }

    private JsonNode sanitize(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            for (Map.Entry<String, JsonNode> entry : object.properties()) {
                if (isSensitiveField(entry.getKey())) {
                    if (!entry.getValue().isNull()) {
                        entry.setValue(JsonNodeFactory.instance.stringNode(REDACTED));
                    }
                } else {
                    entry.setValue(sanitize(entry.getValue()));
                }
            }
            return object;
        }
        if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                array.set(i, sanitize(array.get(i)));
            }
            return array;
        }
        if (node.isString()) {
            JsonNode parsed = parseIfJson(node.stringValue());
            if (parsed.isContainer()) {
                // A JSON document carried as text (e.g. a multipart "data" part)
                return sanitize(parsed);
            }
        }
        if (node.isString() && node.stringValue().length() > MAX_STRING_LENGTH) {
            String value = node.stringValue();
            return JsonNodeFactory.instance.stringNode(value.substring(0, MAX_STRING_LENGTH) + "...[truncated "
                    + (value.length() - MAX_STRING_LENGTH) + " chars]");
        }
        return node;
    }
}
