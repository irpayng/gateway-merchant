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
                .addPathItem("/compliance/profile", new PathItem().put(save))
                .addPathItem("/dashboard", new PathItem().get(new Operation()))
                .addPathItem("/payouts/banks", new PathItem().get(new Operation()))
                .addPathItem("/transactions", new PathItem().get(new Operation()))
                .addPathItem("/customers", new PathItem().get(new Operation()))
                .addPathItem("/health", new PathItem().get(new Operation())));

        config.gatewayOperations().customise(api);

        var dashboard = api.getPaths().get("/dashboard").getGet();
        var payouts = api.getPaths().get("/payouts/banks").getGet();
        var transactions = api.getPaths().get("/transactions").getGet();
        var customers = api.getPaths().get("/customers").getGet();
        var health = api.getPaths().get("/health").getGet();
        assertEquals("bearer", api.getComponents().getSecuritySchemes().get("bearerAuth").getScheme());
        assertTrue(api.getSecurity().getFirst().containsKey("bearerAuth"));
        assertTrue(login.getSecurity().isEmpty());
        assertNull(save.getSecurity());
        assertEquals(List.of("Compliance"), save.getTags());
        assertEquals(List.of("Dashboard"), dashboard.getTags());
        assertEquals(List.of("Payouts"), payouts.getTags());
        assertEquals(List.of("Transactions"), transactions.getTags());
        assertEquals(List.of("Customers"), customers.getTags());
        assertEquals(List.of("Health"), health.getTags());
        assertTrue(save.getParameters().getFirst().getDescription().contains("same key"));
        assertTrue(save.getParameters().get(1).getDescription().contains("Latest application version"));
    }
}
