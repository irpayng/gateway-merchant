package com.tms.gateway.modules.compliance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response containing compliance data.
 */
public record ComplianceResponse(@JsonProperty("compliance_id") Long complianceId,

        @JsonProperty("user_id") Long userId,

        @JsonProperty("merchant_id") String merchantId,

        String status,

        @JsonProperty("current_step") int currentStep,

        @JsonProperty("profile_complete") boolean profileComplete,

        @JsonProperty("contact_complete") boolean contactComplete,

        @JsonProperty("owners_complete") boolean ownersComplete,

        @JsonProperty("bank_accounts_complete") boolean bankAccountsComplete,

        @JsonProperty("agreement_complete") boolean agreementComplete,

        @JsonProperty("rejection_reason") String rejectionReason,

        @JsonProperty("reviewed_by") String reviewedBy,

        @JsonProperty("reviewed_at") String reviewedAt,

        @JsonProperty("created_at") String createdAt,

        @JsonProperty("updated_at") String updatedAt,

        ProfileData profile, ContactData contact, List<OwnerData> owners,
        @JsonProperty("bank_accounts") List<BankAccountData> bankAccounts, AgreementData agreement) {

    public record ProfileData(String country, String industry, String category,
            @JsonProperty("trading_name") String tradingName, @JsonProperty("sales_volume") String salesVolume,
            @JsonProperty("business_type") String businessType, @JsonProperty("business_name") String businessName,
            @JsonProperty("rc_number") String rcNumber, @JsonProperty("cac_url") String cacUrl) {
    }

    public record ContactData(String email, String phone, String address, String city, String state,
            @JsonProperty("postal_code") String postalCode, String country) {
    }

    public record OwnerData(Long id, String role, @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName, String email, String phone, String bvn, String nin,
            @JsonProperty("date_of_birth") String dateOfBirth, String address,
            @JsonProperty("ownership_percentage") Integer ownershipPercentage,
            @JsonProperty("id_document_url") String idDocumentUrl,
            @JsonProperty("id_document_type") String idDocumentType) {
    }

    public record BankAccountData(Long id, @JsonProperty("bank_code") String bankCode,
            @JsonProperty("bank_name") String bankName, @JsonProperty("account_number") String accountNumber,
            @JsonProperty("account_name") String accountName, @JsonProperty("is_primary") boolean isPrimary,
            String currency) {
    }

    public record AgreementData(@JsonProperty("terms_accepted") boolean termsAccepted,
            @JsonProperty("terms_version") String termsVersion,
            @JsonProperty("privacy_accepted") boolean privacyAccepted, @JsonProperty("ip_address") String ipAddress,
            @JsonProperty("user_agent") String userAgent, @JsonProperty("accepted_at") String acceptedAt) {
    }
}
