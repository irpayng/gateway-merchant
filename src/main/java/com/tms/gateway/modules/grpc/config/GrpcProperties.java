package com.tms.gateway.modules.grpc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for gRPC service connections.
 */
@Data
@Component
@ConfigurationProperties(prefix = "grpc")
public class GrpcProperties {

    private int timeout = 30;
    private boolean logging = true;
    private Services services = new Services();

    @Data
    public static class Services {
        private ServiceEndpoint user = new ServiceEndpoint();
        private ServiceEndpoint kyc = new ServiceEndpoint();
        private ServiceEndpoint transaction = new ServiceEndpoint();
        private ServiceEndpoint notification = new ServiceEndpoint();
    }

    @Data
    public static class ServiceEndpoint {
        private String host = "localhost";
        private int port = 8080;
    }

    // ── Convenience getters ──────────────────────────────────

    public String getUserServiceHost() {
        return services.getUser().getHost();
    }

    public int getUserServicePort() {
        return services.getUser().getPort();
    }

    public String getKycServiceHost() {
        return services.getKyc().getHost();
    }

    public int getKycServicePort() {
        return services.getKyc().getPort();
    }

    public String getTransactionServiceHost() {
        return services.getTransaction().getHost();
    }

    public int getTransactionServicePort() {
        return services.getTransaction().getPort();
    }

    public String getNotificationServiceHost() {
        return services.getNotification().getHost();
    }

    public int getNotificationServicePort() {
        return services.getNotification().getPort();
    }
}
