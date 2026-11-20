package com.tms.gateway.modules.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tms.gateway.modules.audit.service.AuditPayloadSanitizer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AuditPayloadSanitizerTest {

    private final AuditPayloadSanitizer sanitizer = new AuditPayloadSanitizer(JsonMapper.builder().build());

    @Test
    void redactsNestedSensitiveFields() {
        Map<String, Object> body = Map.of("user", Map.of("email", "a@b.c", "newPassword", "x"), "keys",
                List.of(Map.of("secret_key", "sk", "public_id", "pk")), "otp", "123456", "transaction_pin", "1234");

        String json = sanitizer.toJson(sanitizer.toTree(body));

        assertThat(json).contains("a@b.c").contains("pk").doesNotContain("\"x\"").doesNotContain("\"sk\"")
                .doesNotContain("123456").doesNotContain("1234\"");
    }

    @Test
    void keepsNonSensitiveLookalikes() {
        assertThat(AuditPayloadSanitizer.isSensitiveField("mapping")).isFalse();
        assertThat(AuditPayloadSanitizer.isSensitiveField("shipping_address")).isFalse();
        assertThat(AuditPayloadSanitizer.isSensitiveField("pin")).isTrue();
        assertThat(AuditPayloadSanitizer.isSensitiveField("Authorization")).isTrue();
    }

    @Test
    void clipsLongStringsAndOversizedPayloads() {
        String longValue = "a".repeat(5_000);
        assertThat(sanitizer.toJson(sanitizer.toTree(Map.of("blob", longValue)))).contains("[truncated 4000 chars]");

        List<String> many = java.util.Collections.nCopies(100, "b".repeat(900));
        String json = sanitizer.toJson(sanitizer.toTree(Map.of("items", many)));
        assertThat(json).contains("\"_truncated\":true").hasSizeLessThan(3_000);
    }

    @Test
    void parsesJsonStringsSoTheirFieldsAreRedacted() {
        String json = sanitizer.toJson(sanitizer.toTree("{\"password\":\"p\",\"name\":\"n\"}"));
        assertThat(json).contains("\"name\":\"n\"").doesNotContain("\"p\"");
    }
}
