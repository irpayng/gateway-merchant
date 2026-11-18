package com.tms.gateway.modules.webhook.controller;

import com.tms.gateway.core.dto.ApiResponse;
import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.modules.webhook.dto.CreateWebhookRequest;
import com.tms.gateway.modules.webhook.dto.CreatedWebhookResponse;
import com.tms.gateway.modules.webhook.dto.UpdateWebhookRequest;
import com.tms.gateway.modules.webhook.dto.WebhookListResponse;
import com.tms.gateway.modules.webhook.service.WebhookService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for webhook management.
 *
 * <p>
 * Endpoints:
 * <ul>
 * <li>GET /webhooks - List all webhooks</li>
 * <li>POST /webhooks - Create a new webhook</li>
 * <li>PATCH /webhooks/{id} - Update an existing webhook</li>
 * <li>DELETE /webhooks/{id} - Delete a webhook</li>
 * <li>POST /webhooks/{id}/test - Send a test event to the webhook</li>
 * </ul>
 */
@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    /**
     * List all webhooks for the logged-in merchant.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<WebhookListResponse>> listWebhooks(
            @AuthenticationPrincipal GatewayMerchantUserDetails user) {

        WebhookListResponse response = webhookService.getWebhooks(user.getUserId());

        return ResponseEntity.ok(ApiResponse.success(response, "Webhooks retrieved"));
    }

    /**
     * Create a new webhook endpoint.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CreatedWebhookResponse>> createWebhook(
            @AuthenticationPrincipal GatewayMerchantUserDetails user,
            @Valid @RequestBody CreateWebhookRequest request) {

        CreatedWebhookResponse response = webhookService.createWebhook(user.getUserId(), request.environment(),
                request.url());

        return ResponseEntity.ok(ApiResponse.success(response, "Webhook created"));
    }

    /**
     * Update an existing webhook.
     */
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> updateWebhook(@AuthenticationPrincipal GatewayMerchantUserDetails user,
            @PathVariable("id") String webhookId, @Valid @RequestBody UpdateWebhookRequest request) {

        webhookService.updateWebhook(user.getUserId(), webhookId, request.url());

        return ResponseEntity.ok(ApiResponse.success(null, "Webhook updated"));
    }

    /**
     * Delete a webhook.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteWebhook(@AuthenticationPrincipal GatewayMerchantUserDetails user,
            @PathVariable("id") String webhookId) {

        webhookService.deleteWebhook(user.getUserId(), webhookId);

        return ResponseEntity.ok(ApiResponse.success(null, "Webhook deleted"));
    }

    /**
     * Test a webhook by sending a test event.
     */
    @PostMapping("/{id}/test")
    public ResponseEntity<ApiResponse<Void>> testWebhook(@AuthenticationPrincipal GatewayMerchantUserDetails user,
            @PathVariable("id") String webhookId) {

        webhookService.testWebhook(user.getUserId(), webhookId);

        return ResponseEntity.ok(ApiResponse.success(null, "Test event sent"));
    }
}
