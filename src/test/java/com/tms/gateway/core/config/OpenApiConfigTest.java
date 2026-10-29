package com.tms.gateway.core.config;

import static org.junit.jupiter.api.Assertions.*;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {
    @Test
    void loginIsPublicWhileComplianceInheritsBearerSecurityAndDocumentsHeaders() {
        var config = new OpenApiConfig();
        var api = config.gatewayOpenApi();
        var login = new Operation();
        var save = new Operation().parameters(List.of(new Parameter().name("Idempotency-Key").in("header"),
                new Parameter().name("X-Expected-Version").in("header")));
        api.setPaths(new Paths().addPathItem("/auth/login", new PathItem().post(login))
                .addPathItem("/compliance/profile", new PathItem().put(save)));

        config.gatewayOperations().customise(api);

        assertEquals("bearer", api.getComponents().getSecuritySchemes().get("bearerAuth").getScheme());
        assertTrue(api.getSecurity().getFirst().containsKey("bearerAuth"));
        assertTrue(login.getSecurity().isEmpty());
        assertNull(save.getSecurity());
        assertEquals(List.of("Compliance"), save.getTags());
        assertTrue(save.getParameters().getFirst().getDescription().contains("same key"));
        assertTrue(save.getParameters().get(1).getDescription().contains("Latest application version"));
    }
}
