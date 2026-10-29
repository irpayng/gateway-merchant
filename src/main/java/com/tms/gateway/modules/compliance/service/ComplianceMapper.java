package com.tms.gateway.modules.compliance.service;
import com.tms.gateway.modules.compliance.dto.*;
import com.tms.gateway.modules.compliance.dto.ComplianceResponse.*;
import com.tms.gateway.modules.grpc.compliance.ComplianceData;
import org.springframework.stereotype.Component;
@Component
public class ComplianceMapper {
    public ComplianceResponse toResponse(ComplianceData d) {
        return new ComplianceResponse(d.getComplianceId(), d.getUserId(), d.getMerchantId(), d.getStatus(),
                d.getCurrentStep(), d.getProfileComplete(), d.getContactComplete(), d.getOwnersComplete(),
                d.getBankAccountsComplete(), d.getAgreementComplete(), d.getRejectionReason(), d.getReviewedBy(),
                d.getReviewedAt(), d.getCreatedAt(), d.getUpdatedAt(), d.hasProfile() ? profile(d.getProfile()) : null,
                d.hasContact() ? contact(d.getContact()) : null, d.getOwnersList().stream().map(this::owner).toList(),
                d.getBankAccountsList().stream().map(this::bankAccount).toList(),
                d.hasAgreement()
                        ? new AgreementData(d.getAgreement().getTermsAccepted(), d.getAgreement().getTermsVersion(),
                                d.getAgreement().getPrivacyAccepted(), d.getAgreement().getIpAddress(),
                                d.getAgreement().getUserAgent(), d.getAgreement().getAcceptedAt())
                        : null,
                d.getVersion(),
                d.getCorrectionsList().stream().map(c -> new CorrectionData(c.getStep(), c.getField(), c.getMessage()))
                        .toList(),
                d.hasRegistration() ? registration(d.getRegistration()) : null, d.getRegistrationComplete(),
                d.getUpdatedBy());
    }
    private ProfileData profile(com.tms.gateway.modules.grpc.compliance.ProfileData d) {
        return new ProfileData(d.getCountry(), d.getIndustry(), d.getCategory(), d.getTradingName(), d.getSalesVolume(),
                d.getBusinessType(), d.getBusinessName(), d.getRcNumber(), d.getCacUrl(), d.getBusinessDescription(),
                d.getCurrency());
    }
    private ContactData contact(com.tms.gateway.modules.grpc.compliance.ContactData d) {
        return new ContactData(d.getEmail(), d.getPhone(), d.getAddress(), d.getCity(), d.getState(), d.getPostalCode(),
                d.getCountry(), d.getSupportEmail(), d.getDisputeEmail(), d.getLga(), d.getSameAsSupportEmail(),
                d.getBusinessAddressDocumentId(),
                d.getOnlineLinksList().stream().map(l -> new OnlineLink(l.getPlatform(), l.getUrl())).toList());
    }
    private OwnerData owner(com.tms.gateway.modules.grpc.compliance.OwnerData d) {
        return new OwnerData(d.getId(), d.getRole(), d.getFirstName(), d.getLastName(), d.getEmail(), d.getPhone(),
                d.getBvn(), d.getNin(), d.getDateOfBirth(), d.getAddress(), d.getOwnershipPercentage(),
                d.getIdDocumentUrl(), d.getIdDocumentType(), d.getFullName(), d.getNationality(), d.getState(),
                d.getLga(), d.getSameAsBusinessAddress(), d.getIdDocumentId(), d.getProofOfAddressDocumentId());
    }
    private BankAccountData bankAccount(com.tms.gateway.modules.grpc.compliance.BankAccountData d) {
        return new BankAccountData(d.getId(), d.getBankCode(), d.getBankName(), d.getAccountNumber(),
                d.getAccountName(), d.getIsPrimary(), d.getCurrency());
    }
    private RegistrationData registration(com.tms.gateway.modules.grpc.compliance.RegistrationData d) {
        return new RegistrationData(d.getRegistrationNumber(), d.getTaxIdentificationNumber());
    }
}
