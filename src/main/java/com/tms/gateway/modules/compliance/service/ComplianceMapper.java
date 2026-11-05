package com.tms.gateway.modules.compliance.service;

import com.tms.gateway.modules.compliance.dto.*;
import com.tms.gateway.modules.compliance.dto.ComplianceResponse.*;
import com.tms.gateway.modules.grpc.compliance.ComplianceData;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ComplianceMapper {

    /**
     * Convert gRPC ComplianceData to the appropriate response structure based on
     * business type. Uses the business type from the user's profile if available,
     * otherwise falls back to the provided fallback business type (from user
     * registration).
     */
    public ComplianceResponse toResponse(ComplianceData d, String fallbackBusinessType) {
        // Determine business type: first from compliance profile, then from user
        // registration
        String businessType = d.hasProfile() && !d.getProfile().getBusinessType().isBlank()
                ? d.getProfile().getBusinessType()
                : fallbackBusinessType;
        boolean isRegistered = "registered".equalsIgnoreCase(businessType);

        // Map owners list
        List<OwnerData> ownersList = d.getOwnersList().stream().map(this::owner).toList();

        if (isRegistered) {
            return toRegisteredResponse(d, ownersList);
        } else {
            return toStarterResponse(d, ownersList);
        }
    }

    /**
     * Build response for starter businesses. Returns profile, contact, owners
     * structure with profile_complete, contact_complete, owners_complete flags.
     */
    private ComplianceResponse toStarterResponse(ComplianceData d, List<OwnerData> ownersList) {
        return new ComplianceResponse(d.getComplianceId(), d.getUserId(), d.getMerchantId(), d.getStatus(),
                d.getCurrentStep(),
                // Starter completion flags
                d.getProfileComplete(), d.getContactComplete(), d.getOwnersComplete(),
                // Registered completion flags (null for starter)
                null, // business_details_complete
                null, // registration_complete
                null, // shareholders_complete
                // Common completion flags
                d.getBankAccountsComplete(), d.getAgreementComplete(), d.getRejectionReason(), d.getReviewedBy(),
                d.getReviewedAt(), d.getCreatedAt(), d.getUpdatedAt(),
                // Starter data
                d.hasProfile() ? profile(d.getProfile()) : null, d.hasContact() ? contact(d.getContact()) : null,
                ownersList.isEmpty() ? null : ownersList,
                // Registered data (null for starter)
                null, // business_details
                null, // registration
                null, // shareholders
                // Common data
                d.getBankAccountsList().stream().map(this::bankAccount).toList(),
                d.hasAgreement() ? agreement(d.getAgreement()) : null, d.getVersion(), d.getCorrectionsList().stream()
                        .map(c -> new CorrectionData(c.getStep(), c.getField(), c.getMessage())).toList(),
                d.getUpdatedBy());
    }

    /**
     * Build response for registered businesses. Returns business_details,
     * registration, shareholders structure with business_details_complete,
     * registration_complete, shareholders_complete flags.
     */
    private ComplianceResponse toRegisteredResponse(ComplianceData d, List<OwnerData> ownersList) {
        // business_details_complete = profile_complete AND contact_complete
        boolean businessDetailsComplete = d.getProfileComplete() && d.getContactComplete();

        return new ComplianceResponse(d.getComplianceId(), d.getUserId(), d.getMerchantId(), d.getStatus(),
                d.getCurrentStep(),
                // Starter completion flags (null for registered)
                null, // profile_complete
                null, // contact_complete
                null, // owners_complete
                // Registered completion flags
                businessDetailsComplete, d.getRegistrationComplete(), d.getOwnersComplete(), // maps to
                                                                                             // shareholders_complete
                // Common completion flags
                d.getBankAccountsComplete(), d.getAgreementComplete(), d.getRejectionReason(), d.getReviewedBy(),
                d.getReviewedAt(), d.getCreatedAt(), d.getUpdatedAt(),
                // Starter data (null for registered)
                null, // profile
                null, // contact
                null, // owners
                // Registered data
                businessDetails(d), d.hasRegistration() ? registration(d.getRegistration()) : null,
                ownersList.isEmpty() ? null : ownersList, // shareholders
                // Common data
                d.getBankAccountsList().stream().map(this::bankAccount).toList(),
                d.hasAgreement() ? agreement(d.getAgreement()) : null, d.getVersion(), d.getCorrectionsList().stream()
                        .map(c -> new CorrectionData(c.getStep(), c.getField(), c.getMessage())).toList(),
                d.getUpdatedBy());
    }

    private ProfileData profile(com.tms.gateway.modules.grpc.compliance.ProfileData d) {
        return new ProfileData(d.getCountry(), d.getIndustry(), d.getCategory(), d.getTradingName(), d.getSalesVolume(),
                d.getBusinessType(), d.getBusinessDescription());
    }

    private ContactData contact(com.tms.gateway.modules.grpc.compliance.ContactData d) {
        return new ContactData(d.getEmail(), d.getPhone(), d.getAddress(), d.getSupportEmail(), d.getDisputeEmail(),
                d.getState(), d.getLga(), d.getSameAsSupportEmail(), d.getBusinessAddressDocumentId(),
                d.getOnlineLinksList().stream().map(l -> new OnlineLink(l.getPlatform(), l.getUrl())).toList());
    }

    /**
     * Combine profile and contact into business_details for registered businesses.
     */
    private BusinessDetailsData businessDetails(ComplianceData d) {
        if (!d.hasProfile() && !d.hasContact()) {
            return null;
        }

        var profile = d.hasProfile() ? d.getProfile() : null;
        var contact = d.hasContact() ? d.getContact() : null;

        return new BusinessDetailsData(profile != null ? profile.getCountry() : null,
                profile != null ? profile.getIndustry() : null, profile != null ? profile.getCategory() : null,
                profile != null ? profile.getTradingName() : null, profile != null ? profile.getSalesVolume() : null,
                profile != null ? profile.getBusinessType() : null,
                profile != null ? profile.getBusinessDescription() : null, contact != null ? contact.getPhone() : null,
                contact != null ? contact.getAddress() : null, contact != null ? contact.getSupportEmail() : null,
                contact != null ? contact.getDisputeEmail() : null, contact != null ? contact.getState() : null,
                contact != null ? contact.getLga() : null, contact != null ? contact.getSameAsSupportEmail() : null,
                contact != null ? contact.getBusinessAddressDocumentId() : null,
                contact != null
                        ? contact.getOnlineLinksList().stream().map(l -> new OnlineLink(l.getPlatform(), l.getUrl()))
                                .toList()
                        : null);
    }

    private OwnerData owner(com.tms.gateway.modules.grpc.compliance.OwnerData d) {
        return new OwnerData(d.getId(), d.getRole(), d.getDateOfBirth(), d.getAddress(), d.getIdDocumentType(),
                d.getFullName(), d.getNationality(), d.getState(), d.getLga(), d.getSameAsBusinessAddress(),
                d.getIdDocumentId(), d.getProofOfAddressDocumentId());
    }

    private BankAccountData bankAccount(com.tms.gateway.modules.grpc.compliance.BankAccountData d) {
        return new BankAccountData(d.getId(), d.getBankCode(), d.getBankName(), d.getAccountNumber(),
                d.getAccountName());
    }

    private RegistrationData registration(com.tms.gateway.modules.grpc.compliance.RegistrationData d) {
        return new RegistrationData(d.getRegistrationNumber(), d.getTaxIdentificationNumber());
    }

    private AgreementData agreement(com.tms.gateway.modules.grpc.compliance.AgreementData d) {
        return new AgreementData(d.getTermsAccepted(), d.getTermsVersion(), d.getIpAddress(), d.getUserAgent(),
                d.getAcceptedAt());
    }
}
