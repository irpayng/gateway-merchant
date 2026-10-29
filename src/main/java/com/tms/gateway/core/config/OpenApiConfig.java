package com.tms.gateway.core.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI gatewayOpenApi() {
        return new OpenAPI().info(new Info().title("Gateway Merchant API").version("v1").description(
                "Log in using POST /auth/login, copy data.token, then click Authorize and paste the token. "
                        + "Compliance belongs to the authenticated merchant. Start or read the application before saving; "
                        + "use each returned version for the next save. Compatible tms-user and tms-kyc services are required."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean
    public OpenApiCustomizer gatewayOperations() {
        Map<String, String> summaries = Map.ofEntries(
                Map.entry("/compliance/start", "Create or resume the merchant application"),
                Map.entry("/compliance", "Read the current application, status and version"),
                Map.entry("/compliance/profile", "Save partial business profile details"),
                Map.entry("/compliance/contact", "Save partial contact and address details"),
                Map.entry("/compliance/registration", "Save partial business registration details"),
                Map.entry("/compliance/owners", "Replace the owners/shareholders collection"),
                Map.entry("/compliance/bank-accounts", "Replace the bank accounts collection"),
                Map.entry("/compliance/submit", "Accept the agreement and submit for review"),
                Map.entry("/compliance/reference-data/{kind}", "Read authoritative reference options"),
                Map.entry("/compliance/documents/uploads", "Create a private document upload session"),
                Map.entry("/compliance/documents/{id}/complete", "Confirm upload and begin inspection"),
                Map.entry("/auth/login", "Log in and obtain a Bearer token"),
                Map.entry("/auth/me", "Read the current user and merchant compliance status"));
        return api -> api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
            String tag = path.startsWith("/auth")
                    ? "Authentication"
                    : path.startsWith("/compliance/documents")
                            ? "Compliance Documents"
                            : path.startsWith("/compliance/reference-data")
                                    ? "Compliance Reference Data"
                                    : path.startsWith("/compliance") ? "Compliance" : "Health";
            operation.setTags(List.of(tag));
            if (summaries.containsKey(path)) {
                operation.setSummary(summaries.get(path));
            } else if (path.equals("/compliance/documents/{id}")) {
                operation.setSummary(method.name().equals("DELETE")
                        ? "Delete an unused draft document"
                        : "Read document status and a short-lived download URL");
            }
            if (path.equals("/health") || (path.equals("/auth/login") && method.name().equals("POST"))) {
                operation.setSecurity(List.of());
            }
            if (path.startsWith("/compliance")) {
                operation.setDescription("Requires an active authenticated merchant member. "
                        + "Pending applications are read-only. Errors: 400 malformed request, 401 unauthenticated, "
                        + "403 forbidden merchant, 404 missing resource, 409 version/replay/state conflict, "
                        + "422 validation failure, 503/504 upstream unavailable or timeout.");
            }
            if (operation.getParameters() != null) {
                operation.getParameters().forEach(parameter -> {
                    if ("Idempotency-Key".equals(parameter.getName())) {
                        parameter.setDescription(
                                "Required mutation identifier: 1-128 ASCII letters, digits, ., _, :, -. "
                                        + "Retry a timed-out request with the same key, version and body.");
                        parameter.setExample("swagger-save-001");
                    } else if ("X-Expected-Version".equals(parameter.getName())) {
                        parameter.setDescription("Latest application version returned by start, read or save. "
                                + "Refresh and reconcile after a 409 before using a new key.");
                        parameter.setExample(1);
                    } else if ("kind".equals(parameter.getName()) && path.contains("reference-data")) {
                        parameter.setDescription("industries, categories, business-types, countries, states, lgas, "
                                + "sales-ranges, currencies, owner-roles, id-document-types");
                        parameter.setExample("countries");
                    }
                });
            }
        }));
    }
}
