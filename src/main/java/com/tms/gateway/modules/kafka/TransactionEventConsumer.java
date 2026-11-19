package com.tms.gateway.modules.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tms.gateway.modules.transaction.entity.PgMerchantTransactionEntity;
import com.tms.gateway.modules.transaction.repository.PgMerchantTransactionRepository;
import com.tms.gateway.modules.user.repository.PgMerchantUserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka consumer for transaction result events from tms-transaction. Replicates
 * transactions locally for fast reads on the merchant dashboard.
 *
 * <p>
 * Listens to: transaction-result topic (published by
 * TransactionResultPublisher) Only processes transactions for pg_merchant users
 * (checked against pg_merchant_users table).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventConsumer {

    private final ObjectMapper objectMapper;
    private final PgMerchantTransactionRepository transactionRepository;
    private final PgMerchantUserRepository userRepository;

    /**
     * Consumes transaction result events from the transaction-result topic.
     *
     * <p>
     * Event structure follows TransactionResultEvent from tms-transaction: -
     * reference, userId, productCode, status, message, amount, amountToPay,
     * serviceFee, channel, metadata, customCharges, providerCode
     */
    @KafkaListener(topics = "transaction-result", groupId = "gateway-merchant-transactions")
    @Transactional
    public void consume(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);

            Long userId = getLong(event, "user_id");
            if (userId == null) {
                log.trace("Skipping transaction event: no user_id");
                return;
            }

            // Only process transactions for pg_merchant users
            if (!userRepository.existsById(userId)) {
                log.trace("Skipping transaction for non-pg_merchant user: userId={}", userId);
                return;
            }

            String reference = getText(event, "reference");
            if (reference == null || reference.isBlank()) {
                log.warn("Transaction event missing reference, skipping");
                return;
            }

            upsertTransaction(event, userId, reference);

        } catch (Exception e) {
            log.error("Failed to process transaction event: {}", e.getMessage(), e);
        }
    }

    private void upsertTransaction(JsonNode event, Long userId, String reference) {
        Instant now = Instant.now();

        PgMerchantTransactionEntity entity = transactionRepository.findByReference(reference).orElseGet(
                () -> PgMerchantTransactionEntity.builder().reference(reference).userId(userId).createdAt(now).build());

        // Update fields from event
        entity.setProductCode(getText(event, "product_code"));
        entity.setAmount(getBigDecimal(event, "amount"));
        entity.setServiceFee(getBigDecimal(event, "service_fee"));
        entity.setStatusCode(normalizeStatus(getText(event, "status")));
        entity.setStatusMessage(getText(event, "message"));
        entity.setChannel(getText(event, "channel"));
        entity.setProviderCode(getText(event, "provider_code"));
        entity.setProviderReference(getText(event, "provider_reference"));

        // Extract metadata fields
        JsonNode metadata = event.get("metadata");
        if (metadata != null && !metadata.isNull()) {
            extractMetadataFields(entity, metadata);
            entity.setMetadata(metadata.toString());
        }

        // Derive payment method from channel or metadata
        if (entity.getPaymentMethod() == null) {
            entity.setPaymentMethod(derivePaymentMethod(event, metadata));
        }

        entity.setUpdatedAt(now);
        entity.setSyncedAt(now);

        transactionRepository.save(entity);
        log.debug("Upserted transaction: reference={}, status={}, userId={}", reference, entity.getStatusCode(),
                userId);
    }

    /**
     * Extract commonly accessed fields from metadata for indexed queries.
     */
    private void extractMetadataFields(PgMerchantTransactionEntity entity, JsonNode metadata) {
        // Customer email - try multiple common paths
        String email = getText(metadata, "customer_email");
        if (email == null)
            email = getText(metadata, "email");
        if (email == null)
            email = getText(metadata, "payer_email");
        entity.setCustomerEmail(email);

        // Customer name
        String name = getText(metadata, "customer_name");
        if (name == null)
            name = getText(metadata, "name");
        if (name == null)
            name = getText(metadata, "payer_name");
        entity.setCustomerName(name);

        // Bank details (for bank transfers)
        entity.setBankName(getText(metadata, "bank_name"));
        entity.setAccountNumber(getText(metadata, "account_number"));

        // Payment method from metadata if present
        String paymentMethod = getText(metadata, "payment_method");
        if (paymentMethod != null) {
            entity.setPaymentMethod(normalizePaymentMethod(paymentMethod));
        }
    }

    /**
     * Derive payment method from channel or other indicators.
     */
    private String derivePaymentMethod(JsonNode event, JsonNode metadata) {
        String channel = getText(event, "channel");
        if (channel != null) {
            return switch (channel.toLowerCase()) {
                case "card", "pos" -> "card";
                case "bank", "bank_transfer", "transfer" -> "bank_transfer";
                case "wallet", "irpay" -> "wallet";
                case "ussd" -> "ussd";
                case "qr" -> "qr";
                default -> channel.toLowerCase();
            };
        }

        // Check product code for hints
        String productCode = getText(event, "product_code");
        if (productCode != null) {
            String lower = productCode.toLowerCase();
            if (lower.contains("card"))
                return "card";
            if (lower.contains("transfer") || lower.contains("nip"))
                return "bank_transfer";
            if (lower.contains("wallet"))
                return "wallet";
        }

        return null;
    }

    /**
     * Normalize status to consistent values for storage and filtering.
     */
    private String normalizeStatus(String status) {
        if (status == null)
            return "pending";
        return switch (status.toLowerCase()) {
            case "successful", "success", "completed" -> "completed";
            case "failed", "failure", "error" -> "failed";
            case "pending", "processing", "initiated" -> "pending";
            case "reversed", "refunded" -> "reversed";
            default -> status.toLowerCase();
        };
    }

    /**
     * Normalize payment method to consistent values.
     */
    private String normalizePaymentMethod(String paymentMethod) {
        if (paymentMethod == null)
            return null;
        return switch (paymentMethod.toLowerCase().replace(" ", "_").replace("-", "_")) {
            case "card", "debit_card", "credit_card" -> "card";
            case "bank_transfer", "bank", "transfer", "nip" -> "bank_transfer";
            case "wallet", "irpay_wallet", "irpay" -> "wallet";
            case "ussd" -> "ussd";
            case "qr", "qr_code" -> "qr";
            default -> paymentMethod.toLowerCase();
        };
    }

    // ── JSON Helpers
    // ──────────────────────────────────────────────────────────────

    private String getText(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }

    private Long getLong(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asLong();
    }

    private BigDecimal getBigDecimal(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        try {
            return new BigDecimal(node.get(field).asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
