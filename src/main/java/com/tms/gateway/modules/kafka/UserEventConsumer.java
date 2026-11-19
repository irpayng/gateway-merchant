package com.tms.gateway.modules.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tms.gateway.modules.user.entity.PgMerchantUserEntity;
import com.tms.gateway.modules.user.repository.PgMerchantUserRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka consumer for user events from tms-user.
 *
 * <p>
 * Listens to the outbox.event.user topic and replicates pg_merchant users into
 * the local database for fast reads. Only processes events for users with type
 * "pg_merchant".
 *
 * <p>
 * Events are published by tms-user via the outbox pattern (Debezium CDC).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventConsumer {

    private final PgMerchantUserRepository userRepository;
    private final ObjectMapper objectMapper;

    /**
     * Consumes user events from the outbox.event.user topic.
     *
     * <p>
     * Event types handled:
     * <ul>
     * <li>UserRegistered — new pg_merchant user created</li>
     * <li>UserUpdated — user profile updated</li>
     * <li>user.blocked — user blocked by admin</li>
     * <li>user.unblocked — user unblocked</li>
     * <li>user.suspended — user suspended</li>
     * <li>user.unsuspended — user unsuspended</li>
     * <li>pg_merchant.email_verified — email verification completed</li>
     * </ul>
     */
    @KafkaListener(topics = "outbox.event.user", groupId = "gateway-merchant-user-replica")
    @Transactional
    public void consume(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);

            // Debezium outbox events have a payload wrapper
            JsonNode payload = root.path("payload").isObject() ? root.get("payload") : root;

            String eventType = getStringField(payload, "type");
            String aggregateType = getStringField(payload, "aggregateType");

            // Only process user-related events
            if (!"user".equals(aggregateType)) {
                return;
            }

            // Parse the actual event data (stored as JSON string in payload field)
            String payloadJson = getStringField(payload, "payload");
            if (payloadJson == null || payloadJson.isBlank()) {
                log.debug("Skipping event with empty payload: type={}", eventType);
                return;
            }

            JsonNode eventData = objectMapper.readTree(payloadJson);

            // Only process events for pg_merchant users
            String userType = getStringField(eventData, "type");
            if (userType == null) {
                userType = getStringField(eventData, "userType");
            }

            // For some events, we need to check the user in the database
            Long userId = getLongField(eventData, "userId");
            if (userId == null) {
                userId = getLongField(eventData, "user_id");
            }

            // Check if this is a pg_merchant user (either from event or existing record)
            boolean isPgMerchant = "pg_merchant".equals(userType);
            if (!isPgMerchant && userId != null) {
                // Check if user already exists in our replica (was previously a pg_merchant)
                isPgMerchant = userRepository.existsById(userId);
            }

            if (!isPgMerchant) {
                log.trace("Skipping non-pg_merchant user event: type={}, userId={}", eventType, userId);
                return;
            }

            switch (eventType) {
                case "UserRegistered", "PgMerchantRegistered" -> handleUserRegistered(eventData);
                case "UserUpdated", "PgMerchantUpdated" -> handleUserUpdated(eventData);
                case "user.blocked" -> handleUserBlocked(eventData);
                case "user.unblocked" -> handleUserUnblocked(eventData);
                case "user.suspended" -> handleUserSuspended(eventData);
                case "user.unsuspended" -> handleUserUnsuspended(eventData);
                case "pg_merchant.email_verified" -> handleEmailVerified(eventData);
                default -> log.trace("Ignoring unhandled event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Failed to process user event: {}", e.getMessage(), e);
        }
    }

    private void handleUserRegistered(JsonNode data) {
        Long userId = getLongField(data, "userId");
        if (userId == null) {
            userId = getLongField(data, "user_id");
        }

        String merchantId = getStringField(data, "merchantId");
        if (merchantId == null) {
            merchantId = getStringField(data, "merchant_id");
        }

        String email = getStringField(data, "email");

        if (userId == null || merchantId == null || email == null) {
            log.warn("UserRegistered event missing required fields: userId={}, merchantId={}, email={}", userId,
                    merchantId, email);
            return;
        }

        // Check if already exists (idempotency)
        if (userRepository.existsById(userId)) {
            log.debug("User already exists in replica, updating: userId={}", userId);
            handleUserUpdated(data);
            return;
        }

        Instant now = Instant.now();
        PgMerchantUserEntity user = PgMerchantUserEntity.builder().id(userId).merchantId(merchantId).email(email)
                .phoneNumber(getStringField(data, "phoneNumber", "phone_number"))
                .businessName(getStringField(data, "businessName", "business_name"))
                .firstName(getStringField(data, "firstName", "first_name"))
                .lastName(getStringField(data, "lastName", "last_name"))
                .emailVerifiedAt(getInstantField(data, "emailVerifiedAt", "email_verified_at")).blockedAt(null)
                .suspendedAt(null).createdAt(getInstantField(data, "createdAt", "created_at", now))
                .updatedAt(getInstantField(data, "updatedAt", "updated_at", now)).syncedAt(now).build();

        userRepository.save(user);
        log.info("Replicated new pg_merchant user: userId={}, merchantId={}, email={}", userId, merchantId, email);
    }

    private void handleUserUpdated(JsonNode data) {
        Long userId = getLongField(data, "userId");
        if (userId == null) {
            userId = getLongField(data, "user_id");
        }

        if (userId == null) {
            log.warn("UserUpdated event missing userId");
            return;
        }

        var existing = userRepository.findById(userId);
        if (existing.isEmpty()) {
            // User not in replica yet, treat as new registration
            handleUserRegistered(data);
            return;
        }

        PgMerchantUserEntity user = existing.get();
        Instant now = Instant.now();

        // Update fields if present in event
        updateIfPresent(data, "merchantId", "merchant_id", user::setMerchantId);
        updateIfPresent(data, "email", user::setEmail);
        updateIfPresent(data, "phoneNumber", "phone_number", user::setPhoneNumber);
        updateIfPresent(data, "businessName", "business_name", user::setBusinessName);
        updateIfPresent(data, "firstName", "first_name", user::setFirstName);
        updateIfPresent(data, "lastName", "last_name", user::setLastName);

        Instant emailVerifiedAt = getInstantField(data, "emailVerifiedAt", "email_verified_at");
        if (emailVerifiedAt != null) {
            user.setEmailVerifiedAt(emailVerifiedAt);
        }

        user.setUpdatedAt(now);
        user.setSyncedAt(now);

        userRepository.save(user);
        log.debug("Updated pg_merchant user replica: userId={}", userId);
    }

    private void handleUserBlocked(JsonNode data) {
        Long userId = getLongField(data, "userId");
        if (userId == null) {
            userId = getLongField(data, "user_id");
        }

        if (userId == null) {
            return;
        }

        var existing = userRepository.findById(userId);
        if (existing.isEmpty()) {
            return;
        }

        PgMerchantUserEntity user = existing.get();
        user.setBlockedAt(Instant.now());
        user.setBlockedReason(getStringField(data, "reason"));
        user.setSyncedAt(Instant.now());

        userRepository.save(user);
        log.info("Marked pg_merchant user as blocked: userId={}", userId);
    }

    private void handleUserUnblocked(JsonNode data) {
        Long userId = getLongField(data, "userId");
        if (userId == null) {
            userId = getLongField(data, "user_id");
        }

        if (userId == null) {
            return;
        }

        var existing = userRepository.findById(userId);
        if (existing.isEmpty()) {
            return;
        }

        PgMerchantUserEntity user = existing.get();
        user.setBlockedAt(null);
        user.setBlockedReason(null);
        user.setSyncedAt(Instant.now());

        userRepository.save(user);
        log.info("Marked pg_merchant user as unblocked: userId={}", userId);
    }

    private void handleUserSuspended(JsonNode data) {
        Long userId = getLongField(data, "userId");
        if (userId == null) {
            userId = getLongField(data, "user_id");
        }

        if (userId == null) {
            return;
        }

        var existing = userRepository.findById(userId);
        if (existing.isEmpty()) {
            return;
        }

        PgMerchantUserEntity user = existing.get();
        user.setSuspendedAt(Instant.now());
        user.setSuspendedReason(getStringField(data, "reason"));
        user.setSyncedAt(Instant.now());

        userRepository.save(user);
        log.info("Marked pg_merchant user as suspended: userId={}", userId);
    }

    private void handleUserUnsuspended(JsonNode data) {
        Long userId = getLongField(data, "userId");
        if (userId == null) {
            userId = getLongField(data, "user_id");
        }

        if (userId == null) {
            return;
        }

        var existing = userRepository.findById(userId);
        if (existing.isEmpty()) {
            return;
        }

        PgMerchantUserEntity user = existing.get();
        user.setSuspendedAt(null);
        user.setSuspendedReason(null);
        user.setSyncedAt(Instant.now());

        userRepository.save(user);
        log.info("Marked pg_merchant user as unsuspended: userId={}", userId);
    }

    private void handleEmailVerified(JsonNode data) {
        Long userId = getLongField(data, "userId");
        if (userId == null) {
            userId = getLongField(data, "user_id");
        }

        if (userId == null) {
            return;
        }

        var existing = userRepository.findById(userId);
        if (existing.isEmpty()) {
            return;
        }

        PgMerchantUserEntity user = existing.get();
        user.setEmailVerifiedAt(Instant.now());
        user.setSyncedAt(Instant.now());

        userRepository.save(user);
        log.info("Marked pg_merchant email as verified: userId={}", userId);
    }

    // ── Helper methods ──────────────────────────────────────────────────────────

    private String getStringField(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (node.has(fieldName) && !node.get(fieldName).isNull()) {
                return node.get(fieldName).asText();
            }
        }
        return null;
    }

    private Long getLongField(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (node.has(fieldName) && !node.get(fieldName).isNull()) {
                return node.get(fieldName).asLong();
            }
        }
        return null;
    }

    private Instant getInstantField(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (node.has(fieldName) && !node.get(fieldName).isNull()) {
                String value = node.get(fieldName).asText();
                try {
                    return Instant.parse(value);
                } catch (Exception e) {
                    // Try parsing as epoch millis
                    try {
                        return Instant.ofEpochMilli(Long.parseLong(value));
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return null;
    }

    private Instant getInstantField(JsonNode node, String field1, String field2, Instant defaultValue) {
        Instant result = getInstantField(node, field1, field2);
        return result != null ? result : defaultValue;
    }

    private void updateIfPresent(JsonNode node, String fieldName, java.util.function.Consumer<String> setter) {
        if (node.has(fieldName) && !node.get(fieldName).isNull()) {
            setter.accept(node.get(fieldName).asText());
        }
    }

    private void updateIfPresent(JsonNode node, String field1, String field2,
            java.util.function.Consumer<String> setter) {
        String value = getStringField(node, field1, field2);
        if (value != null) {
            setter.accept(value);
        }
    }
}
