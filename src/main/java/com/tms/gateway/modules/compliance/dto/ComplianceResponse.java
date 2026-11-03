package com.tms.gateway.modules.compliance.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
public record ComplianceResponse(@JsonProperty("compliance_id") Long complianceId, @JsonProperty("user_id") Long userId,
        @JsonProperty("merchant_id") String merchantId, String status, @JsonProperty("current_step") int currentStep,
        @JsonProperty("profile_complete") boolean profileComplete,
        @JsonProperty("contact_complete") boolean contactComplete,
        @JsonProperty("owners_complete") boolean ownersComplete,
        @JsonProperty("bank_accounts_complete") boolean bankAccountsComplete,
        @JsonProperty("agreement_complete") boolean agreementComplete,
        @JsonProperty("rejection_reason") String rejectionReason, @JsonProperty("reviewed_by") String reviewedBy,
        @JsonProperty("reviewed_at") String reviewedAt, @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt, ProfileData profile, ContactData contact, List<OwnerData> owners,
        @JsonProperty("bank_accounts") List<BankAccountData> bankAccounts, AgreementData agreement, long version,
        List<CorrectionData> corrections, RegistrationData registration,
        @JsonProperty("registration_complete") boolean registrationComplete,
        @JsonProperty("updated_by") long updatedBy) {
    public record ProfileData(@JsonProperty("country") String country, @JsonProperty("industry") String industry,
            @JsonProperty("category") String category, @JsonProperty("trading_name") String tradingName,
            @JsonProperty("sales_volume") String salesVolume, @JsonProperty("business_type") String businessType,
            @JsonProperty("business_description") String businessDescription) {
    }
    public record ContactData(@JsonProperty("email") String email, @JsonProperty("phone") String phone,
            @JsonProperty("address") String address, @JsonProperty("support_email") String supportEmail,
            @JsonProperty("dispute_email") String disputeEmail, @JsonProperty("state") String state,
            @JsonProperty("lga") String lga, @JsonProperty("same_as_support_email") Boolean sameAsSupportEmail,
            @JsonProperty("business_address_document_id") String businessAddressDocumentId,
            @JsonProperty("online_links") List<OnlineLink> onlineLinks) {
    }
    public record OwnerData(@JsonProperty("id") Long id, @JsonProperty("role") String role,
            @JsonProperty("date_of_birth") String dateOfBirth, @JsonProperty("address") String address,
            @JsonProperty("id_document_type") String idDocumentType, @JsonProperty("full_name") String fullName,
            @JsonProperty("nationality") String nationality, @JsonProperty("state") String state,
            @JsonProperty("lga") String lga, @JsonProperty("same_as_business_address") Boolean sameAsBusinessAddress,
            @JsonProperty("id_document_id") String idDocumentId,
            @JsonProperty("proof_of_address_document_id") String proofOfAddressDocumentId) {
    }
    public record BankAccountData(@JsonProperty("id") Long id, @JsonProperty("bank_code") String bankCode,
            @JsonProperty("bank_name") String bankName, @JsonProperty("account_number") String accountNumber,
            @JsonProperty("account_name") String accountName) {
    }
    public record RegistrationData(@JsonProperty("registration_number") String registrationNumber,
            @JsonProperty("tax_identification_number") String taxIdentificationNumber) {
    }
    public record AgreementData(@JsonProperty("terms_accepted") boolean termsAccepted,
            @JsonProperty("terms_version") String termsVersion, @JsonProperty("ip_address") String ipAddress,
            @JsonProperty("user_agent") String userAgent, @JsonProperty("accepted_at") String acceptedAt) {
    }
    public record CorrectionData(String step, String field, String message) {
    }
}
