package com.tms.gateway.modules.compliance.schema;

import static com.tms.gateway.modules.compliance.schema.FieldSchema.OptionValue.of;
import static com.tms.gateway.modules.compliance.schema.FieldSchema.builder;
import static com.tms.gateway.modules.compliance.schema.FieldType.*;

import java.util.List;
import java.util.Map;

/**
 * Compliance schema definitions for different business types. This is the
 * single source of truth for compliance flow structure and field requirements.
 */
public final class ComplianceSchemaDefinitions {

    private ComplianceSchemaDefinitions() {
    }

    private static final Map<BusinessType, ComplianceSchema> SCHEMAS = Map.of(BusinessType.STARTER, starterSchema(),
            BusinessType.REGISTERED, registeredSchema());

    public static ComplianceSchema forBusinessType(BusinessType type) {
        ComplianceSchema schema = SCHEMAS.get(type);
        if (schema == null) {
            throw new IllegalArgumentException("No schema defined for business type: " + type);
        }
        return schema;
    }

    public static ComplianceSchema forBusinessType(String type) {
        return forBusinessType(BusinessType.fromValue(type));
    }

    // ── Starter Business Schema ─────────────────────────────────────────────

    private static ComplianceSchema starterSchema() {
        return ComplianceSchema.builder(BusinessType.STARTER, "Starter Business")
                .description("For sole proprietors and unregistered businesses").steps(starterProfileStep(),
                        starterContactStep(), starterOwnerStep(), bankAccountStep(), agreementStep())
                .build();
    }

    private static StepSchema starterProfileStep() {
        return StepSchema.builder("profile", "Profile details").fields(
                builder("country", "Country", SELECT).required().optionsRef("countries").build(),
                builder("industry", "Industry", SELECT).required().optionsRef("industries").build(),
                builder("category", "Category", SELECT).required().optionsRef("categories")
                        .hint("Depends on selected industry").build(),
                builder("trading_name", "Trading Name", TEXT).required().placeholder("Enter your trading name").build(),
                builder("sales_volume", "Annual Projected Sales Volume", CURRENCY).required().placeholder("NGN 0.00")
                        .build(),
                builder("business_description", "What does your business do?", TEXTAREA).required()
                        .placeholder("Enter a business description...").maxLength(500).build(),
                builder("business_type", "Business Type", SELECT).required()
                        .options(List.of(of("starter", "Starter Business"), of("registered", "Registered Business")))
                        .build())
                .build();
    }

    private static StepSchema starterContactStep() {
        return StepSchema.builder("contact", "Contact information").fields(
                builder("email", "General Email", EMAIL).required().placeholder("Enter an email address").build(),
                builder("support_email", "Support Email", EMAIL).required().placeholder("Enter an email address")
                        .build(),
                builder("dispute_email", "Dispute Email", EMAIL).placeholder("Enter an email address").build(),
                builder("same_as_support_email", "Same as support email", CHECKBOX).build(),
                builder("phone", "Phone Number", PHONE).required().placeholder("Enter phone number").build(),
                builder("address", "Business Address", TEXT).required().placeholder("Enter your business address")
                        .build(),
                builder("state", "State", SELECT).required().optionsRef("states").build(),
                builder("lga", "Local Government Area", SELECT).required().optionsRef("lgas")
                        .hint("Choose from the selected state").build(),
                onlineLinksField()).build();
    }

    private static StepSchema starterOwnerStep() {
        return StepSchema.builder("owners", "Owner details").description("Information about the business owner").fields(
                builder("full_name", "Name", TEXT).required().placeholder("Enter full name").build(),
                builder("date_of_birth", "Date of Birth", DATE).required().placeholder("DD-MM-YYYY").build(),
                builder("nationality", "Nationality", TEXT).required().placeholder("Enter nationality").build(),
                builder("id_document_type", "Identification Document", SELECT).required().options(idDocumentOptions())
                        .build(),
                builder("id_document_id", "Upload ID Document", FILE).required().hint(
                        "Please upload a valid means of identification (National ID Card, NIN, International Passport, Driver's License)")
                        .build(),
                builder("address", "Home address", TEXT).required().placeholder("Enter your home address").build(),
                builder("same_as_business_address", "Same as business address", CHECKBOX).build(),
                builder("state", "State", SELECT).required().optionsRef("states").build(),
                builder("lga", "LGA", SELECT).required().optionsRef("lgas").hint("Choose from the selected state")
                        .build(),
                builder("proof_of_address_document_id", "Upload Proof of Address", FILE).required().hint(
                        "Please upload a valid utility bill not older than 3 months (Electricity, Water or Waste Bill)")
                        .build())
                .build();
    }

    // ── Registered Business Schema ──────────────────────────────────────────

    private static ComplianceSchema registeredSchema() {
        return ComplianceSchema.builder(BusinessType.REGISTERED, "Registered Business")
                .description("For CAC registered companies and enterprises").steps(registeredBusinessDetailsStep(),
                        registrationStep(), shareholdersStep(), bankAccountStep(), agreementStep())
                .build();
    }

    private static StepSchema registeredBusinessDetailsStep() {
        return StepSchema.builder("business_details", "Business details")
                .description("Company profile and contact information")
                .fields(builder("country", "Country", SELECT).required().optionsRef("countries").build(),
                        builder("industry", "Industry", SELECT).required().optionsRef("industries").build(),
                        builder("category", "Category", SELECT).required().optionsRef("categories").build(),
                        builder("trading_name", "Trading Name", TEXT).required().placeholder("Enter your trading name")
                                .build(),
                        builder("sales_volume", "Annual Projected Sales Volume", CURRENCY).required()
                                .placeholder("NGN 0.00").build(),
                        builder("business_description", "What does your business do?", TEXTAREA).required()
                                .placeholder("Enter a business description...").maxLength(500).build(),
                        builder("business_type", "Business Type", SELECT).required()
                                .options(List.of(of("starter", "Starter Business"),
                                        of("registered", "Registered Business")))
                                .build(),
                        builder("support_email", "Support Email", EMAIL).required()
                                .placeholder("Enter an email address").build(),
                        builder("dispute_email", "Dispute Email", EMAIL).placeholder("Enter an email address").build(),
                        builder("same_as_support_email", "Same as support email", CHECKBOX).build(),
                        builder("phone", "Phone Number", PHONE).required().placeholder("Enter phone number").build(),
                        builder("address", "Business Address", TEXT).required()
                                .placeholder("Enter your business address").build(),
                        builder("state", "State", SELECT).required().optionsRef("states").build(),
                        builder("lga", "Local Government Area", SELECT).required().optionsRef("lgas")
                                .hint("Choose from the selected state").build(),
                        builder("business_address_document_id", "Upload Proof of Business Address", FILE).required()
                                .hint("Please upload a valid utility bill not older than 3 months (Electricity, Water or Waste Bill)")
                                .build(),
                        onlineLinksField())
                .build();
    }

    private static StepSchema registrationStep() {
        return StepSchema.builder("registration", "Registration details").description("CAC registration information")
                .fields(builder("registration_number", "Registration Number", TEXT).required()
                        .placeholder("RC1234 or BN1234").build(),
                        builder("tax_identification_number", "Tax Identification Number", TEXT)
                                .placeholder("01234567-0123").build())
                .build();
    }

    private static StepSchema shareholdersStep() {
        return StepSchema.builder("shareholders", "Shareholders").description("Directors and significant shareholders")
                .multiple(1, 10, "Save & add another shareholder")
                .fields(builder("full_name", "Full Legal Name", TEXT).required().placeholder("Enter full legal name")
                        .build(),
                        builder("date_of_birth", "Date of Birth", DATE).required().placeholder("DD-MM-YYYY").build(),
                        builder("nationality", "Nationality", TEXT).required().placeholder("Enter a nationality")
                                .build(),
                        builder("role", "Role at the business", SELECT).required().options(shareholderRoleOptions())
                                .build(),
                        builder("id_document_type", "ID Type", SELECT).required().options(idDocumentOptions()).build(),
                        builder("id_document_id", "Upload ID Document", FILE).required().hint(
                                "Please upload a valid means of identification (National ID Card, NIN, International Passport, Driver's License)")
                                .build(),
                        builder("address", "Home address", TEXT).required().placeholder("Enter your home address")
                                .build(),
                        builder("same_as_business_address", "Same as business address", CHECKBOX).build(),
                        builder("state", "State", SELECT).required().optionsRef("states").build(),
                        builder("lga", "LGA", SELECT).required().optionsRef("lgas")
                                .hint("Choose from the selected state").build(),
                        builder("proof_of_address_document_id", "Upload Proof of Address", FILE).required().hint(
                                "Please upload a valid utility bill not older than 3 months (Electricity, Water or Waste Bill)")
                                .build())
                .build();
    }

    // ── Shared Steps ────────────────────────────────────────────────────────

    private static StepSchema bankAccountStep() {
        return StepSchema.builder("bank_accounts", "Bank account")
                .fields(builder("bank_code", "Bank name", SELECT).required().optionsRef("banks").build(),
                        builder("account_number", "Personal Account Number", TEXT).required().minLength(10)
                                .maxLength(10).placeholder("Enter account number").build(),
                        builder("account_name", "Account Name", TEXT).required().placeholder("Account name")
                                .hint("Verified automatically").build())
                .build();
    }

    private static StepSchema agreementStep() {
        return StepSchema.builder("agreement", "Service Agreement")
                .description("Review and accept the merchant service agreement").fields(builder("agreement_accepted",
                        "I have read and agree to this merchant service agreement", CHECKBOX).required().build())
                .build();
    }

    // ── Field Options ───────────────────────────────────────────────────────

    private static FieldSchema onlineLinksField() {
        return builder("online_links", "Online presence", TEXT).placeholder("Enter a website link")
                .hint("Add and manage links to your online presence").build();
    }

    private static List<FieldSchema.OptionValue> idDocumentOptions() {
        return List.of(of("national_id", "National ID Card"), of("nin", "NIN"),
                of("international_passport", "International Passport"), of("drivers_license", "Driver's License"));
    }

    private static List<FieldSchema.OptionValue> shareholderRoleOptions() {
        return List.of(of("director", "Director"), of("shareholder", "Shareholder"),
                of("beneficial_owner", "Beneficial Owner"), of("signatory", "Signatory"));
    }
}
