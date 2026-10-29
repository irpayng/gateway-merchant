package com.tms.gateway.modules.compliance.service;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tms.gateway.modules.compliance.dto.*;
import java.time.*;
import org.junit.jupiter.api.Test;
class ComplianceDraftValidatorTest {
    private final ObjectMapper json = new ObjectMapper();
    @Test
    void emptyDraftsAreAllowed() throws Exception {
        assertDoesNotThrow(() -> ComplianceDraftValidator.profile(json.readValue("{}", SaveProfileRequest.class)));
        assertDoesNotThrow(() -> ComplianceDraftValidator.contact(json.readValue("{}", SaveContactRequest.class)));
        assertDoesNotThrow(
                () -> ComplianceDraftValidator.owners(json.readValue("{\"owners\":[{}]}", SaveOwnersRequest.class)));
    }
    @Test
    void underageOwnerIsRejected() throws Exception {
        var date = LocalDate.now(ZoneId.of("Africa/Lagos")).minusYears(18).plusDays(1);
        var request = json.readValue("{\"owners\":[{\"date_of_birth\":\"" + date + "\"}]}", SaveOwnersRequest.class);
        assertThrows(ComplianceValidationException.class, () -> ComplianceDraftValidator.owners(request));
    }
    @Test
    void eighteenthBirthdayIsAccepted() throws Exception {
        var date = LocalDate.now(ZoneId.of("Africa/Lagos")).minusYears(18);
        var request = json.readValue("{\"owners\":[{\"date_of_birth\":\"" + date + "\"}]}", SaveOwnersRequest.class);
        assertDoesNotThrow(() -> ComplianceDraftValidator.owners(request));
    }
    @Test
    void invalidDateIsRejected() throws Exception {
        var request = json.readValue("{\"owners\":[{\"date_of_birth\":\"1990-02-31\"}]}", SaveOwnersRequest.class);
        assertThrows(ComplianceValidationException.class, () -> ComplianceDraftValidator.owners(request));
    }
    @Test
    void internationalAccountNumberAndCurrencyAreAccepted() throws Exception {
        var request = json.readValue(
                "{\"bank_accounts\":[{\"account_number\":\"GB82WEST12345698765432\",\"currency\":\"GBP\"}]}",
                SaveBankAccountsRequest.class);
        assertDoesNotThrow(() -> ComplianceDraftValidator.accounts(request));
    }
    @Test
    void unsafeOnlineLinkIsRejected() throws Exception {
        var request = json.readValue("{\"online_links\":[{\"platform\":\"website\",\"url\":\"javascript:alert(1)\"}]}",
                SaveContactRequest.class);
        assertThrows(ComplianceValidationException.class, () -> ComplianceDraftValidator.contact(request));
    }
    @Test
    void publicIdentityDocumentUrlIsRejected() throws Exception {
        var request = json.readValue("{\"owners\":[{\"id_document_url\":\"https://public.test/passport.pdf\"}]}",
                SaveOwnersRequest.class);
        assertThrows(ComplianceValidationException.class, () -> ComplianceDraftValidator.owners(request));
    }
}
