package com.tms.gateway.modules.compliance.service;

import com.tms.gateway.modules.compliance.dto.ComplianceResponse;
import com.tms.gateway.modules.compliance.dto.ComplianceResponse.AgreementData;
import com.tms.gateway.modules.compliance.dto.ComplianceResponse.BankAccountData;
import com.tms.gateway.modules.compliance.dto.ComplianceResponse.ContactData;
import com.tms.gateway.modules.compliance.dto.ComplianceResponse.OwnerData;
import com.tms.gateway.modules.compliance.dto.ComplianceResponse.ProfileData;
import com.tms.gateway.modules.compliance.dto.SaveAgreementRequest;
import com.tms.gateway.modules.compliance.dto.SaveBankAccountRequest;
import com.tms.gateway.modules.compliance.dto.SaveContactRequest;
import com.tms.gateway.modules.compliance.dto.SaveOwnerRequest;
import com.tms.gateway.modules.compliance.dto.SaveProfileRequest;
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
import com.tms.gateway.modules.grpc.compliance.ComplianceData;
import com.tms.gateway.modules.grpc.service.ComplianceGrpcClient;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for PG merchant compliance operations.
 *
 * <p>
 * Hybrid read strategy:
 * <ul>
 * <li>Reads first try the local replica (populated via Kafka)</li>
 * <li>Falls back to gRPC if replica not found (e.g., new compliance before
 * Kafka sync)</li>
 * </ul>
 *
 * <p>
 * All writes go through gRPC to tms-kyc (source of truth).
 */
@Service
public class ComplianceService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceService.class);

    private final ComplianceGrpcClient grpcClient;
    private final PgMerchantComplianceReplicaRepository complianceRepo;
    private final PgMerchantProfileReplicaRepository profileRepo;
    private final PgMerchantContactReplicaRepository contactRepo;
    private final PgMerchantOwnerReplicaRepository ownerRepo;
    private final PgMerchantBankAccountReplicaRepository bankAccountRepo;
    private final PgMerchantAgreementReplicaRepository agreementRepo;

    public ComplianceService(ComplianceGrpcClient grpcClient, PgMerchantComplianceReplicaRepository complianceRepo,
            PgMerchantProfileReplicaRepository profileRepo, PgMerchantContactReplicaRepository contactRepo,
            PgMerchantOwnerReplicaRepository ownerRepo, PgMerchantBankAccountReplicaRepository bankAccountRepo,
            PgMerchantAgreementReplicaRepository agreementRepo) {
        this.grpcClient = grpcClient;
        this.complianceRepo = complianceRepo;
        this.profileRepo = profileRepo;
        this.contactRepo = contactRepo;
        this.ownerRepo = ownerRepo;
        this.bankAccountRepo = bankAccountRepo;
        this.agreementRepo = agreementRepo;
    }

    /**
     * Start a new compliance application (write via gRPC).
     */
    public ComplianceResponse startCompliance(long userId, String merchantId) {
        log.info("Starting compliance for userId={}, merchantId={}", userId, merchantId);
        ComplianceData data = grpcClient.startCompliance(userId, merchantId);
        return toResponse(data);
    }

    /**
     * Get current compliance with full step data. Uses local replica when
     * available, falls back to gRPC.
     */
    public ComplianceResponse getCompliance(long userId) {
        // Try local replica first
        Optional<PgMerchantComplianceReplicaEntity> replicaOpt = complianceRepo.findByUserId(userId);
        if (replicaOpt.isPresent()) {
            log.debug("Serving compliance from local replica for userId={}", userId);
            return toResponseFromReplica(replicaOpt.get());
        }

        // Fallback to gRPC
        log.debug("Replica not found, fetching via gRPC for userId={}", userId);
        ComplianceData data = grpcClient.getCompliance(userId);
        if (data == null) {
            return null;
        }
        return toResponse(data);
    }

    /**
     * Check if user has approved compliance (fast local check).
     */
    public boolean isComplianceApproved(long userId) {
        return complianceRepo.findByUserId(userId).map(PgMerchantComplianceReplicaEntity::isApproved).orElse(false);
    }

    /**
     * Get compliance status only (fast local check with gRPC fallback).
     */
    public String getComplianceStatus(long userId) {
        return complianceRepo.findByUserId(userId).map(PgMerchantComplianceReplicaEntity::getStatus).orElseGet(() -> {
            ComplianceData data = grpcClient.getCompliance(userId);
            return data != null ? data.getStatus() : null;
        });
    }

    /**
     * Save step 1: Profile details (write via gRPC).
     */
    public ComplianceResponse saveProfile(long userId, SaveProfileRequest request) {
        log.info("Saving profile for userId={}", userId);
        ComplianceData data = grpcClient.saveProfile(userId, request);
        return toResponse(data);
    }

    /**
     * Save step 2: Contact information (write via gRPC).
     */
    public ComplianceResponse saveContact(long userId, SaveContactRequest request) {
        log.info("Saving contact for userId={}", userId);
        ComplianceData data = grpcClient.saveContact(userId, request);
        return toResponse(data);
    }

    /**
     * Save step 3: Owners (write via gRPC).
     */
    public ComplianceResponse saveOwners(long userId, List<SaveOwnerRequest> owners) {
        log.info("Saving {} owners for userId={}", owners.size(), userId);
        ComplianceData data = grpcClient.saveOwners(userId, owners);
        return toResponse(data);
    }

    /**
     * Save step 4: Bank accounts (write via gRPC).
     */
    public ComplianceResponse saveBankAccounts(long userId, List<SaveBankAccountRequest> accounts) {
        log.info("Saving {} bank accounts for userId={}", accounts.size(), userId);
        ComplianceData data = grpcClient.saveBankAccounts(userId, accounts);
        return toResponse(data);
    }

    /**
     * Save step 5: Agreement (write via gRPC).
     */
    public ComplianceResponse saveAgreement(long userId, SaveAgreementRequest request) {
        log.info("Saving agreement for userId={}", userId);
        ComplianceData data = grpcClient.saveAgreement(userId, request);
        return toResponse(data);
    }

    /**
     * Submit compliance for review (write via gRPC).
     */
    public ComplianceResponse submitCompliance(long userId) {
        log.info("Submitting compliance for userId={}", userId);
        ComplianceData data = grpcClient.submitCompliance(userId);
        return toResponse(data);
    }

    // ── Mapping from gRPC response ────────────────────────────

    private ComplianceResponse toResponse(ComplianceData data) {
        ProfileData profile = null;
        if (data.hasProfile()) {
            var p = data.getProfile();
            profile = new ProfileData(emptyToNull(p.getCountry()), emptyToNull(p.getIndustry()),
                    emptyToNull(p.getCategory()), emptyToNull(p.getTradingName()), emptyToNull(p.getSalesVolume()),
                    emptyToNull(p.getBusinessType()), emptyToNull(p.getBusinessName()), emptyToNull(p.getRcNumber()),
                    emptyToNull(p.getCacUrl()));
        }

        ContactData contact = null;
        if (data.hasContact()) {
            var c = data.getContact();
            contact = new ContactData(emptyToNull(c.getEmail()), emptyToNull(c.getPhone()), emptyToNull(c.getAddress()),
                    emptyToNull(c.getCity()), emptyToNull(c.getState()), emptyToNull(c.getPostalCode()),
                    emptyToNull(c.getCountry()));
        }

        List<OwnerData> owners = null;
        if (data.getOwnersCount() > 0) {
            owners = data.getOwnersList().stream()
                    .map(o -> new OwnerData(o.getId() > 0 ? o.getId() : null, emptyToNull(o.getRole()),
                            emptyToNull(o.getFirstName()), emptyToNull(o.getLastName()), emptyToNull(o.getEmail()),
                            emptyToNull(o.getPhone()), emptyToNull(o.getBvn()), emptyToNull(o.getNin()),
                            emptyToNull(o.getDateOfBirth()), emptyToNull(o.getAddress()),
                            o.getOwnershipPercentage() > 0 ? o.getOwnershipPercentage() : null,
                            emptyToNull(o.getIdDocumentUrl()), emptyToNull(o.getIdDocumentType())))
                    .toList();
        }

        List<BankAccountData> bankAccounts = null;
        if (data.getBankAccountsCount() > 0) {
            bankAccounts = data.getBankAccountsList().stream()
                    .map(b -> new BankAccountData(b.getId() > 0 ? b.getId() : null, emptyToNull(b.getBankCode()),
                            emptyToNull(b.getBankName()), emptyToNull(b.getAccountNumber()),
                            emptyToNull(b.getAccountName()), b.getIsPrimary(), emptyToNull(b.getCurrency())))
                    .toList();
        }

        AgreementData agreement = null;
        if (data.hasAgreement()) {
            var a = data.getAgreement();
            agreement = new AgreementData(a.getTermsAccepted(), emptyToNull(a.getTermsVersion()),
                    a.getPrivacyAccepted(), emptyToNull(a.getIpAddress()), emptyToNull(a.getUserAgent()),
                    emptyToNull(a.getAcceptedAt()));
        }

        return new ComplianceResponse(data.getComplianceId(), data.getUserId(), emptyToNull(data.getMerchantId()),
                emptyToNull(data.getStatus()), data.getCurrentStep(), data.getProfileComplete(),
                data.getContactComplete(), data.getOwnersComplete(), data.getBankAccountsComplete(),
                data.getAgreementComplete(), emptyToNull(data.getRejectionReason()), emptyToNull(data.getReviewedBy()),
                emptyToNull(data.getReviewedAt()), emptyToNull(data.getCreatedAt()), emptyToNull(data.getUpdatedAt()),
                profile, contact, owners, bankAccounts, agreement);
    }

    // ── Mapping from local replica ────────────────────────────

    private ComplianceResponse toResponseFromReplica(PgMerchantComplianceReplicaEntity c) {
        ProfileData profile = null;
        Optional<PgMerchantProfileReplicaEntity> profileOpt = profileRepo.findByComplianceId(c.getId());
        if (profileOpt.isPresent()) {
            var p = profileOpt.get();
            profile = new ProfileData(p.getCountry(), p.getIndustry(), p.getCategory(), p.getTradingName(),
                    p.getSalesVolume(), p.getBusinessType(), p.getBusinessName(), p.getRcNumber(), p.getCacUrl());
        }

        ContactData contact = null;
        Optional<PgMerchantContactReplicaEntity> contactOpt = contactRepo.findByComplianceId(c.getId());
        if (contactOpt.isPresent()) {
            var ct = contactOpt.get();
            contact = new ContactData(ct.getEmail(), ct.getPhone(), ct.getAddress(), ct.getCity(), ct.getState(),
                    ct.getPostalCode(), ct.getCountry());
        }

        List<OwnerData> owners = null;
        List<PgMerchantOwnerReplicaEntity> ownerEntities = ownerRepo.findByComplianceId(c.getId());
        if (!ownerEntities.isEmpty()) {
            owners = ownerEntities.stream()
                    .map(o -> new OwnerData(o.getSourceId(), o.getRole(), o.getFirstName(), o.getLastName(),
                            o.getEmail(), o.getPhone(), o.getBvn(), o.getNin(), o.getDateOfBirth(), o.getAddress(),
                            o.getOwnershipPercentage(), o.getIdDocumentUrl(), o.getIdDocumentType()))
                    .toList();
        }

        List<BankAccountData> bankAccounts = null;
        List<PgMerchantBankAccountReplicaEntity> bankEntities = bankAccountRepo.findByComplianceId(c.getId());
        if (!bankEntities.isEmpty()) {
            bankAccounts = bankEntities.stream().map(b -> new BankAccountData(b.getSourceId(), b.getBankCode(),
                    b.getBankName(), b.getAccountNumber(), b.getAccountName(), b.isPrimary(), b.getCurrency()))
                    .toList();
        }

        AgreementData agreement = null;
        Optional<PgMerchantAgreementReplicaEntity> agreementOpt = agreementRepo.findByComplianceId(c.getId());
        if (agreementOpt.isPresent()) {
            var a = agreementOpt.get();
            agreement = new AgreementData(a.isTermsAccepted(), a.getTermsVersion(), a.isPrivacyAccepted(),
                    a.getIpAddress(), a.getUserAgent(),
                    a.getAcceptedAt() != null ? a.getAcceptedAt().toString() : null);
        }

        return new ComplianceResponse(c.getSourceId(), c.getUserId(), c.getMerchantId(), c.getStatus(),
                c.getCurrentStep(), c.isProfileComplete(), c.isContactComplete(), c.isOwnersComplete(),
                c.isBankAccountsComplete(), c.isAgreementComplete(), c.getRejectionReason(), c.getReviewedBy(),
                c.getReviewedAt() != null ? c.getReviewedAt().toString() : null,
                c.getCreatedAt() != null ? c.getCreatedAt().toString() : null,
                c.getUpdatedAt() != null ? c.getUpdatedAt().toString() : null, profile, contact, owners, bankAccounts,
                agreement);
    }

    private String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }
}
