package com.tms.gateway;

import jakarta.annotation.PostConstruct;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.TimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Gateway Merchant Dashboard - Spring Boot backend for Payment Gateway
 * merchants (pg_merchant user type).
 *
 * <p>
 * This service provides a scalable dashboard by:
 * <ul>
 * <li>Replicating data locally via Kafka (no gRPC for reads)</li>
 * <li>Using gRPC only for write operations to source-of-truth
 * microservices</li>
 * <li>Authenticating via tms-user (same credentials as registration)</li>
 * </ul>
 */
@SpringBootApplication
@EnableAsync
public class GatewayMerchantApplication {

    private static final Logger log = LoggerFactory.getLogger(GatewayMerchantApplication.class);

    /**
     * Business timezone. Empty (the default) means "inherit the JVM default", which
     * is driven by the {@code TZ} environment variable set on the container.
     */
    @Value("${app.timezone:}")
    private String configuredTimezone;

    public static void main(String[] args) {
        SpringApplication.run(GatewayMerchantApplication.class, args);
    }

    /**
     * Aligns the JVM default zone with the configured business zone.
     */
    @PostConstruct
    void setTimezone() {
        if (configuredTimezone == null || configuredTimezone.isBlank()) {
            log.info("Business timezone: inheriting JVM default {} (from the TZ environment variable)",
                    TimeZone.getDefault().getID());
            return;
        }
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(configuredTimezone.trim())));
            log.info("Business timezone set to {} (from app.timezone)", TimeZone.getDefault().getID());
        } catch (DateTimeException e) {
            log.warn("Invalid app.timezone='{}' — keeping JVM default {}", configuredTimezone,
                    TimeZone.getDefault().getID());
        }
    }
}
