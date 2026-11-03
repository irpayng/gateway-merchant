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
    void unsafeOnlineLinkIsRejected() throws Exception {
        var request = json.readValue("{\"online_links\":[{\"platform\":\"website\",\"url\":\"javascript:alert(1)\"}]}",
                SaveContactRequest.class);
        assertThrows(ComplianceValidationException.class, () -> ComplianceDraftValidator.contact(request));
    }
    @Test
    void registeredBusinessSalesVolumeIsAnAmountWithoutCurrency() throws Exception {
        var valid = json.readValue("{\"sales_volume\":\"250000.00\"}", RegisteredBusinessDetailsRequest.class);
        assertDoesNotThrow(() -> ComplianceDraftValidator.registeredBusinessDetails(valid));

        var invalid = json.readValue("{\"sales_volume\":\"NGN 250,000\"}", RegisteredBusinessDetailsRequest.class);
        assertThrows(ComplianceValidationException.class,
                () -> ComplianceDraftValidator.registeredBusinessDetails(invalid));
        var whitespace = json.readValue("{\"sales_volume\":\"  \"}", RegisteredBusinessDetailsRequest.class);
        assertThrows(ComplianceValidationException.class,
                () -> ComplianceDraftValidator.registeredBusinessDetails(whitespace));
    }
    @Test
    void registeredBusinessEndpointRejectsAnyNonRegisteredBusinessType() throws Exception {
        var request = json.readValue("{\"business_type\":\"\"}", RegisteredBusinessDetailsRequest.class);
        assertThrows(ComplianceValidationException.class,
                () -> ComplianceDraftValidator.registeredBusinessDetails(request));
    }
    @Test
    void registeredShareholderDateMatchesScreenFormatAndAgeRule() throws Exception {
        var adult = LocalDate.now(ZoneId.of("Africa/Lagos")).minusYears(18)
                .format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        var valid = json.readValue("{\"shareholders\":[{\"date_of_birth\":\"" + adult + "\"}]}",
                SaveRegisteredShareholdersRequest.class);
        assertDoesNotThrow(() -> ComplianceDraftValidator.registeredShareholders(valid));

        var invalid = json.readValue("{\"shareholders\":[{\"date_of_birth\":\"1990-02-31\"}]}",
                SaveRegisteredShareholdersRequest.class);
        assertThrows(ComplianceValidationException.class,
                () -> ComplianceDraftValidator.registeredShareholders(invalid));
    }
    @Test
    void registeredShareholderCollectionHonorsSchemaMaximum() throws Exception {
        var shareholders = java.util.stream.IntStream.range(0, 11)
                .mapToObj(index -> "{\"full_name\":\"Owner " + index + "\"}")
                .collect(java.util.stream.Collectors.joining(","));
        var request = json.readValue("{\"shareholders\":[" + shareholders + "]}",
                SaveRegisteredShareholdersRequest.class);
        assertThrows(ComplianceValidationException.class,
                () -> ComplianceDraftValidator.registeredShareholders(request));
    }
}
