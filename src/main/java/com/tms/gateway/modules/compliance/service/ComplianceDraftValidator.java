package com.tms.gateway.modules.compliance.service;
import com.tms.gateway.modules.compliance.dto.*;
import com.tms.gateway.modules.compliance.dto.ComplianceResponse.CorrectionData;
import java.net.URI;
import java.time.*;
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
        currency(r.currency(), "profile");
    }
    public static void contact(SaveContactRequest r) {
        if (r.onlineLinks() != null)
            for (var link : r.onlineLinks()) {
                if (link == null)
                    fail("contact", "online_links", "Links cannot contain null entries");
                if (supplied(link.url())) {
                    try {
                        var uri = URI.create(link.url());
                        if (uri.getScheme() == null
                                || !Set.of("http", "https").contains(uri.getScheme().toLowerCase(Locale.ROOT))
                                || uri.getHost() == null || uri.getUserInfo() != null)
                            throw new IllegalArgumentException();
                    } catch (IllegalArgumentException e) {
                        fail("contact", "online_links", "Use an absolute HTTP or HTTPS URL without credentials");
                    }
                }
            }
    }
    public static void owners(SaveOwnersRequest r) {
        for (int i = 0; i < r.owners().size(); i++) {
            var o = r.owners().get(i);
            String prefix = "owners[" + i + "].";
            if (supplied(o.dateOfBirth())) {
                LocalDate birth;
                try {
                    birth = LocalDate.parse(o.dateOfBirth());
                } catch (java.time.format.DateTimeParseException e) {
                    fail("owners", prefix + "date_of_birth", "Use YYYY-MM-DD");
                    return;
                }
                if (birth.plusYears(18).isAfter(LocalDate.now(ZoneId.of("Africa/Lagos"))))
                    fail("owners", prefix + "date_of_birth", "Owner must be at least 18 years old");
            }
            if (o.ownershipPercentage() != null && (o.ownershipPercentage() < 0 || o.ownershipPercentage() > 100))
                fail("owners", prefix + "ownership_percentage", "Use a percentage from 0 to 100");
            if (supplied(o.idDocumentUrl()))
                fail("owners", prefix + "id_document_url", "Use a private document ID instead of a public URL");
        }
    }
    public static void accounts(SaveBankAccountsRequest r) {
        long primary = r.bankAccounts().stream().filter(a -> Boolean.TRUE.equals(a.isPrimary())).count();
        if (primary > 1)
            fail("bank_accounts", "is_primary", "Only one account may be primary");
        for (var account : r.bankAccounts())
            currency(account.currency(), "bank_accounts");
    }
    private static void currency(String value, String step) {
        if (supplied(value))
            try {
                Currency.getInstance(value);
            } catch (IllegalArgumentException e) {
                fail(step, "currency", "Use an ISO 4217 currency code");
            }
    }
}
