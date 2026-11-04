package com.tms.gateway.modules.compliance.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tms.gateway.modules.compliance.dto.*;
import com.tms.gateway.modules.grpc.compliance.ComplianceData;
import org.junit.jupiter.api.Test;

class ComplianceDesignContractTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void legacyUpstreamFieldsAreNotExposedToFrontend() {
        var data = ComplianceData.newBuilder()
                .setProfile(com.tms.gateway.modules.grpc.compliance.ProfileData.newBuilder().setTradingName("ACME")
                        .setBusinessName("Legacy").setRcNumber("RC123").setCacUrl("https://legacy.test/cac")
                        .setCurrency("NGN"))
                .setContact(
                        com.tms.gateway.modules.grpc.compliance.ContactData.newBuilder().setEmail("contact@acme.test")
                                .setCity("Lagos").setState("Lagos").setCountry("NG").setPostalCode("100001"))
                .addOwners(com.tms.gateway.modules.grpc.compliance.OwnerData.newBuilder().setFullName("John Doe")
                        .setFirstName("John").setLastName("Doe").setEmail("owner@acme.test").setPhone("123")
                        .setBvn("123").setNin("123").setState("Lagos").setOwnershipPercentage(100)
                        .setIdDocumentUrl("https://legacy.test/id"))
                .addBankAccounts(com.tms.gateway.modules.grpc.compliance.BankAccountData.newBuilder()
                        .setAccountNumber("1234567890").setIsPrimary(true).setCurrency("NGN"))
                .setAgreement(com.tms.gateway.modules.grpc.compliance.AgreementData.newBuilder().setTermsAccepted(true)
                        .setPrivacyAccepted(true))
                .build();
        var response = json.valueToTree(new ComplianceMapper().toResponse(data));
        absent(response.get("profile"), "business_name", "rc_number", "cac_url", "currency");
        absent(response.get("contact"), "city", "postal_code", "country");
        absent(response.get("owners").get(0), "first_name", "last_name", "email", "phone", "bvn", "nin",
                "ownership_percentage", "id_document_url");
        absent(response.get("bank_accounts").get(0), "is_primary", "currency");
        absent(response.get("agreement"), "privacy_accepted");
        assertEquals("ACME", response.at("/profile/trading_name").asText());
        assertEquals("Lagos", response.at("/contact/state").asText());
        assertEquals("Lagos", response.at("/owners/0/state").asText());
        assertEquals("John Doe", response.at("/owners/0/full_name").asText());
        assertEquals("1234567890", response.at("/bank_accounts/0/account_number").asText());
    }

    @Test
    void removedFieldsAreAbsentFromRequestContracts() {
        absent(json.valueToTree(empty(SaveProfileRequest.class)), "business_name", "rc_number", "cac_url", "currency");
        absent(json.valueToTree(empty(SaveContactRequest.class)), "city", "postal_code", "country");
        absent(json.valueToTree(empty(SaveOwnerRequest.class)), "first_name", "last_name", "email", "phone", "bvn",
                "nin", "ownership_percentage", "id_document_url");
        absent(json.valueToTree(empty(SaveBankAccountRequest.class)), "is_primary", "currency");
    }

    @Test
    void stateIsExposedOnContactAndOwnerRequests() {
        var contact = new SaveContactRequest(null, null, null, null, null, "Lagos", "ikeja", null, null);
        var owner = new SaveOwnerRequest(null, null, null, null, null, null, null, "Lagos", "ikeja", null);

        assertEquals("Lagos", json.valueToTree(contact).get("state").asText());
        assertEquals("Lagos", json.valueToTree(owner).get("state").asText());
    }

    private Object empty(Class<?> type) {
        try {
            return json.readValue("{}", type);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private void absent(com.fasterxml.jackson.databind.JsonNode node, String... fields) {
        for (String field : fields)
            assertFalse(node.has(field), field + " must not be exposed");
    }
}
