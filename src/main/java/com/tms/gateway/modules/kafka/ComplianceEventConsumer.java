package com.tms.gateway.modules.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tms.gateway.modules.compliance.entity.PgMerchantAgreementReplicaEntity;
import com.tms.gateway.modules.compliance.entity.PgMerchantBankAccountReplicaEntity;
import com.tms.gateway.modules.compliance.entity.PgMerchantComplianceReplicaEntity;
import com.tms.gateway.modules.compliance.entity.PgMerchantContactReplicaEntity;
import com.tms.gateway.modules.compliance.entity.PgMerchantOwnerReplicaEntity;
import com.tms.gateway.modules.compliance.entity.PgMerchantProfileReplicaEntity;
import com.tms.gateway.modules.compliance.repository.PgMerchantAgreementReplicaRepository;
import com.tms.gateway.modules.compliance.repository.PgMerchantBankAccountReplicaRepository;
import com.tms.gateway.modules.compliance.repository.PgMerchantComplianceReplicaRepository;
import com.tms.gateway.modules.compliance.repository.PgMerchantContactReplicaRepository;
import com.tms.gateway.modules.compliance.repository.PgMerchantOwnerReplicaRepository;
import com.tms.gateway.modules.compliance.repository.PgMerchantProfileReplicaRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka consumer for PG merchant compliance events from tms-kyc. Replicates
 * compliance data locally for fast reads without gRPC calls.
 *
 * <p>
 * Listens to: outbox.event.kyc (filtered for pg_merchant.compliance.* events)
 */
@Component
public class ComplianceEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ComplianceEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final PgMerchantComplianceReplicaRepository complianceRepo;
    private final PgMerchantProfileReplicaRepository profileRepo;
    private final PgMerchantContactReplicaRepository contactRepo;
    private final PgMerchantOwnerReplicaRepository ownerRepo;
    private final PgMerchantBankAccountReplicaRepository bankAccountRepo;
    private final PgMerchantAgreementReplicaRepository agreementRepo;

    public ComplianceEventConsumer(ObjectMapper objectMapper, PgMerchantComplianceReplicaRepository complianceRepo,
            PgMerchantProfileReplicaRepository profileRepo, PgMerchantContactReplicaRepository contactRepo,
            PgMerchantOwnerReplicaRepository ownerRepo, PgMerchantBankAccountReplicaRepository bankAccountRepo,
            PgMerchantAgreementReplicaRepository agreementRepo) {
        this.objectMapper = objectMapper;
        this.complianceRepo = complianceRepo;
        this.profileRepo = profileRepo;
        this.contactRepo = contactRepo;
        this.ownerRepo = ownerRepo;
        this.bankAccountRepo = bankAccountRepo;
        this.agreementRepo = agreementRepo;
    }

    @KafkaListener(topics = "outbox.event.kyc", groupId = "gateway-merchant-compliance")
    @Transactional
    public void consumeKycEvent(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode payload = root.has("payload") ? root.get("payload") : root;

            String eventType = getText(payload, "type");
            if (eventType == null || !eventType.startsWith("pg_merchant.compliance.")) {
                return; // Not a PG merchant compliance event
            }

            String payloadJson = getText(payload, "payload");
            if (payloadJson == null) {
                log.warn("Empty payload in compliance event: {}", eventType);
                return;
            }

            JsonNode eventData = objectMapper.readTree(payloadJson);
            log.info("Processing compliance event: {} for userId={}", eventType, getLong(eventData, "user_id"));

            processComplianceEvent(eventType, eventData);

        } catch (Exception e) {
            log.error("Failed to process compliance event: {}", e.getMessage(), e);
        }
    }

    private void processComplianceEvent(String eventType, JsonNode data) {
        Long sourceId = getLong(data, "compliance_id");
        Long userId = getLong(data, "user_id");
        String merchantId = getText(data, "merchant_id");

        if (sourceId == null || userId == null) {
            log.warn("Invalid compliance event - missing compliance_id or user_id");
            return;
        }

        // Upsert the master compliance record
        PgMerchantComplianceReplicaEntity compliance = complianceRepo.findBySourceId(sourceId)
                .orElseGet(() -> PgMerchantComplianceReplicaEntity.builder().sourceId(sourceId).userId(userId)
                        .merchantId(merchantId).createdAt(Instant.now()).build());

        compliance.setStatus(getText(data, "status"));
        compliance.setCurrentStep(getInt(data, "current_step"));
        compliance.setProfileComplete(getBoolean(data, "profile_complete"));
        compliance.setContactComplete(getBoolean(data, "contact_complete"));
        compliance.setOwnersComplete(getBoolean(data, "owners_complete"));
        compliance.setBankAccountsComplete(getBoolean(data, "bank_accounts_complete"));
        compliance.setAgreementComplete(getBoolean(data, "agreement_complete"));
        compliance.setRejectionReason(getText(data, "rejection_reason"));
        compliance.setReviewedBy(getText(data, "reviewed_by"));
        compliance.setReviewedAt(getInstant(data, "reviewed_at"));
        compliance.setUpdatedAt(Instant.now());
        compliance.setSyncedAt(Instant.now());

        compliance = complianceRepo.save(compliance);
        log.debug("Upserted compliance replica: sourceId={}, status={}", sourceId, compliance.getStatus());

        // Process step data if included
        if (data.has("profile") && !data.get("profile").isNull()) {
            upsertProfile(compliance, data.get("profile"));
        }
        if (data.has("contact") && !data.get("contact").isNull()) {
            upsertContact(compliance, data.get("contact"));
        }
        if (data.has("owners") && data.get("owners").isArray()) {
            replaceOwners(compliance, data.get("owners"));
        }
        if (data.has("bank_accounts") && data.get("bank_accounts").isArray()) {
            replaceBankAccounts(compliance, data.get("bank_accounts"));
        }
        if (data.has("agreement") && !data.get("agreement").isNull()) {
            upsertAgreement(compliance, data.get("agreement"));
        }
    }

    private void upsertProfile(PgMerchantComplianceReplicaEntity compliance, JsonNode data) {
        PgMerchantProfileReplicaEntity profile = profileRepo.findByComplianceId(compliance.getId())
                .orElseGet(() -> PgMerchantProfileReplicaEntity.builder().compliance(compliance)
                        .userId(compliance.getUserId()).createdAt(Instant.now()).build());

        profile.setCountry(getText(data, "country"));
        profile.setIndustry(getText(data, "industry"));
        profile.setCategory(getText(data, "category"));
        profile.setTradingName(getText(data, "trading_name"));
        profile.setSalesVolume(getText(data, "sales_volume"));
        profile.setBusinessType(getText(data, "business_type"));
        profile.setBusinessName(getText(data, "business_name"));
        profile.setRcNumber(getText(data, "rc_number"));
        profile.setCacUrl(getText(data, "cac_url"));
        profile.setUpdatedAt(Instant.now());
        profile.setSyncedAt(Instant.now());

        profileRepo.save(profile);
    }

    private void upsertContact(PgMerchantComplianceReplicaEntity compliance, JsonNode data) {
        PgMerchantContactReplicaEntity contact = contactRepo.findByComplianceId(compliance.getId())
                .orElseGet(() -> PgMerchantContactReplicaEntity.builder().compliance(compliance)
                        .userId(compliance.getUserId()).createdAt(Instant.now()).build());

        contact.setEmail(getText(data, "email"));
        contact.setPhone(getText(data, "phone"));
        contact.setAddress(getText(data, "address"));
        contact.setCity(getText(data, "city"));
        contact.setState(getText(data, "state"));
        contact.setPostalCode(getText(data, "postal_code"));
        contact.setCountry(getText(data, "country"));
        contact.setUpdatedAt(Instant.now());
        contact.setSyncedAt(Instant.now());

        contactRepo.save(contact);
    }

    private void replaceOwners(PgMerchantComplianceReplicaEntity compliance, JsonNode ownersArray) {
        // Delete existing owners and replace with new list
        ownerRepo.deleteByComplianceId(compliance.getId());
        ownerRepo.flush();

        for (JsonNode ownerData : ownersArray) {
            PgMerchantOwnerReplicaEntity owner = PgMerchantOwnerReplicaEntity.builder()
                    .sourceId(getLong(ownerData, "id")).compliance(compliance).userId(compliance.getUserId())
                    .role(getText(ownerData, "role")).firstName(getText(ownerData, "first_name"))
                    .lastName(getText(ownerData, "last_name")).email(getText(ownerData, "email"))
                    .phone(getText(ownerData, "phone")).bvn(getText(ownerData, "bvn")).nin(getText(ownerData, "nin"))
                    .dateOfBirth(getText(ownerData, "date_of_birth")).address(getText(ownerData, "address"))
                    .ownershipPercentage(getInteger(ownerData, "ownership_percentage"))
                    .idDocumentUrl(getText(ownerData, "id_document_url"))
                    .idDocumentType(getText(ownerData, "id_document_type")).createdAt(Instant.now())
                    .updatedAt(Instant.now()).syncedAt(Instant.now()).build();
            ownerRepo.save(owner);
        }
    }

    private void replaceBankAccounts(PgMerchantComplianceReplicaEntity compliance, JsonNode accountsArray) {
        // Delete existing accounts and replace with new list
        bankAccountRepo.deleteByComplianceId(compliance.getId());
        bankAccountRepo.flush();

        for (JsonNode accountData : accountsArray) {
            PgMerchantBankAccountReplicaEntity account = PgMerchantBankAccountReplicaEntity.builder()
                    .sourceId(getLong(accountData, "id")).compliance(compliance).userId(compliance.getUserId())
                    .bankCode(getText(accountData, "bank_code")).bankName(getText(accountData, "bank_name"))
                    .accountNumber(getText(accountData, "account_number"))
                    .accountName(getText(accountData, "account_name")).isPrimary(getBoolean(accountData, "is_primary"))
                    .currency(getText(accountData, "currency")).createdAt(Instant.now()).updatedAt(Instant.now())
                    .syncedAt(Instant.now()).build();
            bankAccountRepo.save(account);
        }
    }

    private void upsertAgreement(PgMerchantComplianceReplicaEntity compliance, JsonNode data) {
        PgMerchantAgreementReplicaEntity agreement = agreementRepo.findByComplianceId(compliance.getId())
                .orElseGet(() -> PgMerchantAgreementReplicaEntity.builder().compliance(compliance)
                        .userId(compliance.getUserId()).createdAt(Instant.now()).build());

        agreement.setTermsAccepted(getBoolean(data, "terms_accepted"));
        agreement.setTermsVersion(getText(data, "terms_version"));
        agreement.setPrivacyAccepted(getBoolean(data, "privacy_accepted"));
        agreement.setIpAddress(getText(data, "ip_address"));
        agreement.setUserAgent(getText(data, "user_agent"));
        agreement.setAcceptedAt(getInstant(data, "accepted_at"));
        agreement.setUpdatedAt(Instant.now());
        agreement.setSyncedAt(Instant.now());

        agreementRepo.save(agreement);
    }

    // ── JSON Helpers ──────────────────────────────────────────

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

    private Integer getInteger(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asInt();
    }

    private int getInt(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return 0;
        }
        return node.get(field).asInt();
    }

    private boolean getBoolean(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return false;
        }
        return node.get(field).asBoolean();
    }

    private Instant getInstant(JsonNode node, String field) {
        String text = getText(node, field);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (Exception e) {
            return null;
        }
    }
}
