package com.tms.gateway.modules.payout.service;

import com.tms.gateway.core.dto.PagedResponse;
import com.tms.gateway.modules.payout.dto.PayoutDetailDto;
import com.tms.gateway.modules.payout.dto.PayoutListItemDto;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PayoutHistoryService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Africa/Lagos");
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT));
    private static final List<String> SUPPORTED_STATUSES = List.of("completed", "pending", "failed");
    private final PayoutDataProvider payoutDataProvider;

    public Map<String, Object> list(Long userId, String search, String status, String startDate, String endDate,
            int page, int size) {
        DateRange dateRange = parseDateRange(startDate, endDate);
        validateFilters(status, dateRange, page, size);
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        String normalizedStatus = status == null || status.isBlank() ? null : status.trim().toLowerCase(Locale.ROOT);

        List<PayoutRecord> matching = matchingRecords(userId, query, normalizedStatus, dateRange);

        long offset = (long) (page - 1) * size;
        int from = (int) Math.min(offset, matching.size());
        int to = (int) Math.min((long) from + size, matching.size());
        List<PayoutListItemDto> data = matching.subList(from, to).stream().map(this::toListItem).toList();
        return PagedResponse.from(data, matching.size(), page, size, "/payouts");
    }

    public String exportCsv(Long userId, String search, String status, String startDate, String endDate) {
        DateRange dateRange = parseDateRange(startDate, endDate);
        validateFilterValues(status, dateRange);
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        String normalizedStatus = status == null || status.isBlank() ? null : status.trim().toLowerCase(Locale.ROOT);
        StringBuilder csv = new StringBuilder(
                "\"Payment Reference\",\"Total Value\",\"Payout Value\",\"Receiving Bank\",\"Status\",\"Sales Date\",\"Date, Time Settled\"\r\n");
        matchingRecords(userId, query, normalizedStatus, dateRange).stream()
                .map(payout -> String.join(",", csvCell(payout.paymentReference()),
                        csvCell(payout.totalValue().toPlainString()), csvCell(payout.payoutValue().toPlainString()),
                        csvCell(payout.bankName()), csvCell(statusDisplay(payout.status())),
                        csvCell(payout.salesDate().toString()),
                        csvCell(payout.dateTimeSettled() == null ? "" : payout.dateTimeSettled().toString())))
                .forEach(row -> csv.append(row).append("\r\n"));
        return csv.toString();
    }

    public PayoutDetailDto getDetail(Long userId, String payoutId) {
        if (payoutId == null || payoutId.isBlank()) {
            throw new IllegalArgumentException("payoutId must not be blank");
        }
        PayoutRecord payout = payoutDataProvider.get(userId, payoutId);
        return new PayoutDetailDto(payout.payoutId(), payout.payoutValue(), payout.currencyCode(),
                payout.status(), statusDisplay(payout.status()), payout.paymentReference(), payout.totalValue(),
                payout.payoutValue(), payout.dateTimeSettled(),
                new PayoutDetailDto.ReceivingBank(payout.bankName(), payout.accountName(), payout.accountNumber()));
    }

    private List<PayoutRecord> matchingRecords(Long userId, String query, String status, DateRange dateRange) {
        return payoutDataProvider.list(userId).stream()
                .filter(payout -> status == null || payout.status().equals(status))
                .filter(payout -> dateRange.start() == null || !payout.salesDate().isBefore(dateRange.start()))
                .filter(payout -> dateRange.endExclusive() == null
                        || payout.salesDate().isBefore(dateRange.endExclusive()))
                .filter(payout -> matches(payout, query)).toList();
    }

    private void validateFilters(String status, DateRange dateRange, int page, int size) {
        validateFilterValues(status, dateRange);
        if (page < 1) {
            throw new IllegalArgumentException("page must be 1 or greater");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be greater than zero");
        }
    }

    private void validateFilterValues(String status, DateRange dateRange) {
        if (status != null && !status.isBlank()
                && !SUPPORTED_STATUSES.contains(status.trim().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("status must be completed, pending, or failed");
        }
        if (dateRange.start() != null && dateRange.endExclusive() != null
                && !dateRange.start().isBefore(dateRange.endExclusive())) {
            throw new IllegalArgumentException("start_date must be earlier than end_date");
        }
    }

    private DateRange parseDateRange(String startDate, String endDate) {
        return new DateRange(parseDate(startDate, false), parseDate(endDate, true));
    }

    private Instant parseDate(String value, boolean endDate) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                LocalDate date = LocalDate.parse(trimmed, format);
                LocalDate boundary = endDate ? date.plusDays(1) : date;
                return boundary.atStartOfDay(BUSINESS_ZONE).toInstant();
            } catch (DateTimeParseException ignored) {
                // Try the next supported date representation.
            } catch (DateTimeException ex) {
                throw new IllegalArgumentException("Date is outside the supported range");
            }
        }
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(trimmed).toInstant();
            } catch (DateTimeParseException ignoredOffset) {
                try {
                    return LocalDateTime.parse(trimmed).atZone(BUSINESS_ZONE).toInstant();
                } catch (DateTimeParseException ignoredLocal) {
                    throw new IllegalArgumentException(
                            "Dates must use yyyy-MM-dd, dd/MM/yyyy, or an ISO-8601 date-time");
                }
            }
        }
    }

    private String csvCell(String value) {
        String safeValue = value.matches("^[\\s]*[=+@-].*") ? "'" + value : value;
        return "\"" + safeValue.replace("\"", "\"\"") + "\"";
    }

    private boolean matches(PayoutRecord payout, String query) {
        if (query.isBlank()) {
            return true;
        }
        return payout.paymentReference().toLowerCase(Locale.ROOT).contains(query)
                || payout.bankName().toLowerCase(Locale.ROOT).contains(query)
                || payout.accountName().toLowerCase(Locale.ROOT).contains(query)
                || payout.accountNumber().toLowerCase(Locale.ROOT).contains(query);
    }

    private PayoutListItemDto toListItem(PayoutRecord payout) {
        return new PayoutListItemDto(payout.payoutId(), payout.paymentReference(), payout.totalValue(),
                payout.payoutValue(), payout.currencyCode(), payout.bankName(), payout.status(),
                statusDisplay(payout.status()), payout.salesDate(), payout.dateTimeSettled());
    }

    private String statusDisplay(String status) {
        return status.substring(0, 1).toUpperCase(Locale.ROOT) + status.substring(1);
    }

    private record DateRange(Instant start, Instant endExclusive) {
    }
}
