package com.tms.gateway.modules.compliance.service;
import com.tms.gateway.modules.compliance.dto.*;
import com.tms.gateway.modules.compliance.dto.ComplianceResponse.CorrectionData;
import java.net.URI;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;
/**
 * Validate values that are supplied; completeness belongs to atomic upstream
 * submission.
 */
public final class ComplianceDraftValidator {
    private ComplianceDraftValidator() {
    }
    private static boolean supplied(String s) {
        return s != null && !s.isBlank();
    }
    private static void fail(String step, String field, String message) {
        throw new ComplianceValidationException(List.of(new CorrectionData(step, field, message)));
    }
    public static void profile(SaveProfileRequest r) {
        if (supplied(r.businessType()) && !Set.of("starter", "registered").contains(r.businessType()))
            fail("profile", "business_type", "Use starter or registered");
    }
    public static void contact(SaveContactRequest r) {
        links(r.onlineLinks(), "contact");
    }
    public static void registeredBusinessDetails(RegisteredBusinessDetailsRequest r) {
        if (r.businessType() != null && !"registered".equals(r.businessType()))
            fail("business_details", "business_type", "Use registered for this endpoint");
        if (r.salesVolume() != null && !r.salesVolume().isEmpty() && !r.salesVolume().matches("\\d+(\\.\\d+)?"))
            fail("business_details", "sales_volume", "Use a decimal amount without a currency symbol");
        links(r.onlineLinks(), "business_details");
    }
    public static void owners(SaveOwnersRequest r) {
        for (int i = 0; i < r.owners().size(); i++) {
            var o = r.owners().get(i);
            validateDateOfBirth(o.dateOfBirth(), "owners", "owners[" + i + "].date_of_birth");
        }
    }
    public static void registeredShareholders(SaveRegisteredShareholdersRequest r) {
        if (r.shareholders().size() > 10)
            fail("shareholders", "shareholders", "A maximum of 10 shareholders is allowed");
        for (int i = 0; i < r.shareholders().size(); i++)
            validateRegisteredDateOfBirth(r.shareholders().get(i).dateOfBirth(), i);
    }
    private static void validateRegisteredDateOfBirth(String value, int index) {
        if (value != null && !value.isEmpty()) {
            LocalDate birth;
            try {
                birth = LocalDate.parse(value,
                        DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT));
            } catch (java.time.format.DateTimeParseException e) {
                fail("shareholders", "shareholders[" + index + "].date_of_birth", "Use DD-MM-YYYY");
                return;
            }
            if (birth.plusYears(18).isAfter(LocalDate.now(ZoneId.of("Africa/Lagos"))))
                fail("shareholders", "shareholders[" + index + "].date_of_birth",
                        "Shareholder must be at least 18 years old");
        }
    }
    private static void validateDateOfBirth(String value, String step, String field) {
        if (supplied(value)) {
            LocalDate birth;
            try {
                birth = LocalDate.parse(value);
            } catch (java.time.format.DateTimeParseException e) {
                fail(step, field, "Use YYYY-MM-DD");
                return;
            }
            if (birth.plusYears(18).isAfter(LocalDate.now(ZoneId.of("Africa/Lagos"))))
                fail(step, field, "Owner must be at least 18 years old");
        }
    }
    private static void links(List<OnlineLink> links, String step) {
        if (links == null)
            return;
        for (var link : links) {
            if (link == null)
                fail(step, "online_links", "Links cannot contain null entries");
            if (supplied(link.url())) {
                try {
                    var uri = URI.create(link.url());
                    if (uri.getScheme() == null
                            || !Set.of("http", "https").contains(uri.getScheme().toLowerCase(Locale.ROOT))
                            || uri.getHost() == null || uri.getUserInfo() != null)
                        throw new IllegalArgumentException();
                } catch (IllegalArgumentException e) {
                    fail(step, "online_links", "Use an absolute HTTP or HTTPS URL without credentials");
                }
            }
        }
    }
}
