package com.tms.gateway.modules.compliance.service;

import com.tms.gateway.core.exception.AppException;
import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.modules.compliance.controller.ComplianceController.OwnerFiles;
import com.tms.gateway.modules.compliance.dto.*;
import com.tms.gateway.modules.grpc.compliance.ComplianceData;
import com.tms.gateway.modules.grpc.compliance.MutationContext;
import com.tms.gateway.modules.grpc.service.ComplianceGrpcClient;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Authoritative reads avoid stale drafts and approval decisions while Kafka
 * catches up.
 */
@Service
@RequiredArgsConstructor
public class ComplianceService {

    private final ComplianceGrpcClient grpcClient;
    private final ComplianceMapper mapper;

    /**
     * Thread-local to pass business type from controller to mapper without changing
     * all method signatures.
     */
    private static final ThreadLocal<String> BUSINESS_TYPE_CONTEXT = new ThreadLocal<>();

    public MutationContext context(GatewayMerchantUserDetails user, String key, Long version) {
        if (user == null) {
            throw new AppException("Authentication required", HttpStatus.UNAUTHORIZED);
        }
        if (!user.isEnabled()) {
            throw new AppException("Account is disabled or not verified", HttpStatus.FORBIDDEN);
        }
        if (user.getUserId() == null) {
            throw new AppException("User ID is missing", HttpStatus.FORBIDDEN);
        }
        if (user.getMerchantId() == null || user.getMerchantId().isBlank()) {
            throw new AppException("Merchant ID is not assigned. Please contact support.", HttpStatus.FORBIDDEN);
        }
        if (key != null && (!key.matches("[A-Za-z0-9._:-]{1,128}")))
            throw new AppException("Invalid Idempotency-Key");
        if (version != null && version < 0)
            throw new AppException("Expected version must be nonnegative");

        // Store the user's business type for the mapper to use
        BUSINESS_TYPE_CONTEXT.set(user.getBusinessType());

        return MutationContext.newBuilder().setActorUserId(user.getUserId()).setMerchantId(user.getMerchantId())
                .setIdempotencyKey(key == null ? "" : key).setExpectedVersion(version == null ? 0 : version).build();
    }

    public MutationContext mutation(GatewayMerchantUserDetails user, String key, long version) {
        if (key == null || key.isBlank())
            throw new AppException("Idempotency-Key is required");
        return context(user, key, version);
    }

    private ComplianceResponse checked(MutationContext c, ComplianceData d) {
        try {
            if (d == null)
                return null;
            if (!c.getMerchantId().equals(d.getMerchantId()))
                throw new AppException("KYC returned a different merchant", HttpStatus.BAD_GATEWAY);
            return mapper.toResponse(d, BUSINESS_TYPE_CONTEXT.get());
        } finally {
            BUSINESS_TYPE_CONTEXT.remove();
        }
    }

    public ComplianceResponse startCompliance(MutationContext c) {
        return checked(c, grpcClient.startCompliance(c));
    }

    public ComplianceResponse getCompliance(MutationContext c) {
        return checked(c, grpcClient.getCompliance(c));
    }

    public ComplianceResponse saveProfile(MutationContext c, SaveProfileRequest r) {
        ComplianceDraftValidator.profile(r);
        return checked(c, grpcClient.saveProfile(c, r));
    }

    public ComplianceResponse saveContact(MutationContext c, SaveContactRequest r) {
        ComplianceDraftValidator.contact(r);
        return checked(c, grpcClient.saveContact(c, r));
    }

    public ComplianceResponse saveOwners(MutationContext c, SaveOwnersRequest r) {
        ComplianceDraftValidator.owners(r);
        return checked(c, grpcClient.saveOwners(c, r.owners()));
    }

    public ComplianceResponse saveBankAccounts(MutationContext c, SaveBankAccountsRequest r) {
        return checked(c, grpcClient.saveBankAccounts(c, r.bankAccounts()));
    }

    public ComplianceResponse saveRegistration(MutationContext c, SaveRegistrationRequest r) {
        return checked(c, grpcClient.saveRegistration(c, r));
    }

    // ── Methods with file uploads ───────────────────────────────

    public ComplianceResponse saveProfileWithFile(MutationContext c, SaveProfileRequest r,
            MultipartFile cacCertificate) {
        ComplianceDraftValidator.profile(r);
        return checked(c, grpcClient.saveProfileWithFile(c, r, cacCertificate));
    }

    public ComplianceResponse saveContactWithFile(MutationContext c, SaveContactRequest r, MultipartFile addressProof) {
        ComplianceDraftValidator.contact(r);
        return checked(c, grpcClient.saveContactWithFile(c, r, addressProof));
    }

    public ComplianceResponse saveOwnersWithFiles(MutationContext c, SaveOwnersRequest r,
            Map<Integer, OwnerFiles> ownerFiles) {
        ComplianceDraftValidator.owners(r);
        return checked(c, grpcClient.saveOwnersWithFiles(c, r.owners(), ownerFiles));
    }

    public ComplianceResponse saveShareholdersWithFiles(MutationContext c, SaveRegisteredShareholdersRequest r,
            Map<Integer, OwnerFiles> ownerFiles) {
        ComplianceDraftValidator.registeredShareholders(r);
        List<SaveOwnerRequest> owners = r.shareholders().stream().map(shareholder -> {
            String dateOfBirth = shareholder.dateOfBirth();
            if (dateOfBirth != null && !dateOfBirth.isEmpty())
                dateOfBirth = LocalDate.parse(dateOfBirth, DateTimeFormatter.ofPattern("dd-MM-uuuu")).toString();
            return new SaveOwnerRequest(null, shareholder.role(), dateOfBirth, shareholder.address(),
                    shareholder.idDocumentType(), shareholder.fullName(), shareholder.nationality(),
                    shareholder.state(), shareholder.lga(), shareholder.sameAsBusinessAddress());
        }).toList();
        return checked(c, grpcClient.saveOwnersWithFiles(c, owners, ownerFiles));
    }

    public ComplianceResponse saveRegisteredBusinessDetails(MutationContext c, RegisteredBusinessDetailsRequest r) {
        return saveRegisteredBusinessDetailsWithFile(c, r, null);
    }

    public ComplianceResponse saveRegisteredBusinessDetailsWithFile(MutationContext c,
            RegisteredBusinessDetailsRequest r, MultipartFile businessAddressProof) {
        ComplianceDraftValidator.registeredBusinessDetails(r);
        SaveProfileRequest profile = new SaveProfileRequest(r.country(), r.industry(), r.category(), r.tradingName(),
                r.salesVolume(), r.businessType(), r.businessDescription());
        SaveContactRequest contact = new SaveContactRequest(null, r.phone(), r.address(), r.supportEmail(),
                r.disputeEmail(), r.state(), r.lga(), r.sameAsSupportEmail(), r.onlineLinks());

        boolean hasProfileFields = r.country() != null || r.industry() != null || r.category() != null
                || r.tradingName() != null || r.salesVolume() != null || r.businessType() != null
                || r.businessDescription() != null;
        boolean hasContactFields = r.phone() != null || r.address() != null || r.supportEmail() != null
                || r.disputeEmail() != null || r.state() != null || r.lga() != null || r.sameAsSupportEmail() != null
                || r.onlineLinks() != null;
        boolean hasFile = businessAddressProof != null && !businessAddressProof.isEmpty();

        if (!hasProfileFields && !hasContactFields && !hasFile)
            throw new ComplianceValidationException(List.of(new ComplianceResponse.CorrectionData("business_details",
                    "business_details", "At least one field must be supplied")));

        ComplianceResponse response = null;
        MutationContext current = c;
        String savedBusinessType = BUSINESS_TYPE_CONTEXT.get();
        try {
            if (hasProfileFields) {
                response = checkedRequired(current, grpcClient.saveProfile(current, profile));
                current = withExpectedVersion(c, response.version());
                // Restore business type context for next call
                BUSINESS_TYPE_CONTEXT.set(savedBusinessType);
            }
            if (hasContactFields || hasFile) {
                try {
                    response = checkedRequired(current,
                            grpcClient.saveContactWithFile(current, contact, businessAddressProof));
                } catch (AppException e) {
                    if (hasProfileFields)
                        throw new CompliancePartialSaveException(e, response.version());
                    throw e;
                } catch (ComplianceValidationException e) {
                    if (hasProfileFields)
                        throw new CompliancePartialSaveException(e, response.version());
                    throw e;
                }
            }
        } finally {
            BUSINESS_TYPE_CONTEXT.remove();
        }
        return response;
    }

    public ComplianceResponse saveRegisteredBusinessRegistration(MutationContext c,
            RegisteredBusinessRegistrationRequest r) {
        var request = new SaveRegistrationRequest(null, r.registrationNumber(), r.taxIdentificationNumber());
        return checked(c, grpcClient.saveRegistration(c, request));
    }

    public ComplianceResponse saveRegisteredBusinessShareholders(MutationContext c,
            SaveRegisteredShareholdersRequest r) {
        ComplianceDraftValidator.registeredShareholders(r);
        List<SaveOwnerRequest> owners = r.shareholders().stream().map(shareholder -> {
            String dateOfBirth = shareholder.dateOfBirth();
            if (dateOfBirth != null && !dateOfBirth.isEmpty())
                dateOfBirth = LocalDate.parse(dateOfBirth, DateTimeFormatter.ofPattern("dd-MM-uuuu")).toString();
            return new SaveOwnerRequest(null, shareholder.role(), dateOfBirth, shareholder.address(),
                    shareholder.idDocumentType(), shareholder.fullName(), shareholder.nationality(),
                    shareholder.state(), shareholder.lga(), shareholder.sameAsBusinessAddress());
        }).toList();
        return checkedRequired(c, grpcClient.saveOwners(c, owners));
    }

    private ComplianceResponse checkedRequired(MutationContext context, ComplianceData data) {
        if (data == null)
            throw new AppException("KYC returned no compliance data", HttpStatus.BAD_GATEWAY);
        if (!context.getMerchantId().equals(data.getMerchantId()))
            throw new AppException("KYC returned a different merchant", HttpStatus.BAD_GATEWAY);
        return mapper.toResponse(data, BUSINESS_TYPE_CONTEXT.get());
    }

    private MutationContext withExpectedVersion(MutationContext context, long version) {
        return MutationContext.newBuilder().setActorUserId(context.getActorUserId())
                .setMerchantId(context.getMerchantId()).setIdempotencyKey(context.getIdempotencyKey())
                .setExpectedVersion(version).build();
    }

    public ComplianceResponse submitCompliance(MutationContext c, SubmitComplianceRequest r, String ip, String agent) {
        if (!Boolean.TRUE.equals(r.agreementAccepted()))
            throw new AppException("Agreement must be accepted", HttpStatus.UNPROCESSABLE_ENTITY);
        return checked(c, grpcClient.submitCompliance(c, true, ip, agent));
    }
}
