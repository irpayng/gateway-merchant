package com.tms.gateway.modules.webhook.service;

import com.b2b.grpc.CreateWebhookResponse;
import com.b2b.grpc.DeleteWebhookResponse;
import com.b2b.grpc.GetWebhooksResponse;
import com.b2b.grpc.TestWebhookResponse;
import com.b2b.grpc.UpdateWebhookResponse;
import com.b2b.grpc.WebhookCreated;
import com.b2b.grpc.WebhookInfo;
import com.tms.gateway.modules.grpc.service.B2bGrpcClient;
import com.tms.gateway.modules.webhook.dto.CreatedWebhookResponse;
import com.tms.gateway.modules.webhook.dto.WebhookListResponse;
import com.tms.gateway.modules.webhook.dto.WebhookResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for webhook management operations. All operations go through gRPC to
 * tms-b2b (source of truth).
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final B2bGrpcClient b2bGrpcClient;

    public WebhookService(B2bGrpcClient b2bGrpcClient) {
        this.b2bGrpcClient = b2bGrpcClient;
    }

    /**
     * Get all webhooks for a pg_merchant user.
     */
    public WebhookListResponse getWebhooks(long userId) {
        log.debug("Getting webhooks for userId={}", userId);

        GetWebhooksResponse response = b2bGrpcClient.getWebhooks(userId);

        List<WebhookResponse> webhooks = response.getWebhooksList().stream().map(this::toWebhookResponse).toList();

        return new WebhookListResponse(webhooks);
    }

    /**
     * Create a new webhook endpoint.
     */
    public CreatedWebhookResponse createWebhook(long userId, String environment, String url) {
        log.info("Creating webhook for userId={}, environment={}", userId, environment);

        CreateWebhookResponse response = b2bGrpcClient.createWebhook(userId, environment, url);

        return toCreatedWebhookResponse(response.getWebhook());
    }

    /**
     * Update an existing webhook.
     */
    public void updateWebhook(long userId, String webhookId, String url) {
        log.info("Updating webhook webhookId={} for userId={}", webhookId, userId);

        UpdateWebhookResponse response = b2bGrpcClient.updateWebhook(userId, webhookId, url);

        log.debug("Update result: success={}, message={}", response.getSuccess(), response.getMessage());
    }

    /**
     * Delete a webhook.
     */
    public void deleteWebhook(long userId, String webhookId) {
        log.info("Deleting webhook webhookId={} for userId={}", webhookId, userId);

        DeleteWebhookResponse response = b2bGrpcClient.deleteWebhook(userId, webhookId);

        log.debug("Delete result: success={}, message={}", response.getSuccess(), response.getMessage());
    }

    /**
     * Test a webhook by sending a test event.
     */
    public void testWebhook(long userId, String webhookId) {
        log.info("Testing webhook webhookId={} for userId={}", webhookId, userId);

        TestWebhookResponse response = b2bGrpcClient.testWebhook(userId, webhookId);

        log.debug("Test result: success={}, message={}", response.getSuccess(), response.getMessage());
    }

    // ── Mapping helpers ──

    private WebhookResponse toWebhookResponse(WebhookInfo info) {
        return new WebhookResponse(emptyToNull(info.getId()), emptyToNull(info.getEnvironment()),
                emptyToNull(info.getUrl()), emptyToNull(info.getCreatedAt()), emptyToNull(info.getUpdatedAt()));
    }

    private CreatedWebhookResponse toCreatedWebhookResponse(WebhookCreated created) {
        return new CreatedWebhookResponse(emptyToNull(created.getId()), emptyToNull(created.getEnvironment()),
                emptyToNull(created.getUrl()), emptyToNull(created.getSecret()));
    }

    private String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }
}
