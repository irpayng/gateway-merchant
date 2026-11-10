package com.tms.gateway.modules.refund.service;

import com.google.protobuf.Timestamp;
import com.tms.gateway.core.exception.AppException;
import com.tms.gateway.grpc.transaction.GetRefundRequest;
import com.tms.gateway.grpc.transaction.ListRefundsRequest;
import com.tms.gateway.grpc.transaction.ListRefundsResponse;
import com.tms.gateway.grpc.transaction.Refund;
import com.tms.gateway.grpc.transaction.RefundStatus;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "refunds.mock.enabled", havingValue = "true")
public class MockRefundDataProvider implements RefundDataProvider {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Africa/Lagos");
    private static final Instant CREATED_AT = LocalDateTime.of(2025, 7, 15, 8, 20).atZone(BUSINESS_ZONE).toInstant();
    private static final Instant REFUNDED_AT = LocalDateTime.of(2025, 7, 15, 9, 20).atZone(BUSINESS_ZONE).toInstant();
    private static final List<Refund> REFUNDS = createRefunds();

    private static List<Refund> createRefunds() {
        List<Refund> refunds = new ArrayList<>(List.of(
                refund("refund-john-doe", "b5e4b374-5cb1-4138-8dff-fdd2d3de8ca9", "John Doe", "john.doe@example.com",
                        "john@email.com", "Shoprite Supermarket", "shoprite@gmail.com", "b5e4b374"),
                refund("refund-peter-griffin", "transaction-peter-griffin", "Peter Griffin", "petergriffin@example.com",
                        "john@email.com", "Shoprite Supermarket", "shoprite@gmail.com", "b5e4b374"),
                refund("refund-joseph-adeyemi", "transaction-joseph-adeyemi", "Joseph Adeyemi", "adeyemi@example.com",
                        "john@email.com", "Shoprite Supermarket", "shoprite@gmail.com", "b5e4b374"),
                refund("refund-matthew-paul", "transaction-matthew-paul", "Matthew Paul", "matthew@example.com",
                        "john@email.com", "Shoprite Supermarket", "shoprite@gmail.com", "b5e4b374"),
                refund("refund-andrew-emmanuel", "transaction-andrew-emmanuel", "Andrew Emmanuel",
                        "emmanuel@example.com", "john@email.com", "Shoprite Supermarket", "shoprite@gmail.com",
                        "b5e4b374")));

        for (int i = 6; i <= 50; i++) {
            String suffix = String.format(Locale.ROOT, "%02d", i);
            refunds.add(refund("refund-sample-" + suffix, "transaction-sample-" + suffix, "Sample Customer " + suffix,
                    "sample.customer." + suffix + "@example.com", "john@email.com", "Shoprite Supermarket",
                    "shoprite@gmail.com", "b5e4b374"));
        }
        return List.copyOf(refunds);
    }

    @Override
    public ListRefundsResponse list(ListRefundsRequest request) {
        String query = request.getSearch().trim().toLowerCase(Locale.ROOT);
        Instant from = request.hasCreatedAtFrom() ? toInstant(request.getCreatedAtFrom()) : null;
        Instant to = request.hasCreatedAtTo() ? toInstant(request.getCreatedAtTo()) : null;

        List<Refund> matching = REFUNDS.stream()
                .filter(refund -> request.getStatus() == RefundStatus.REFUND_STATUS_UNSPECIFIED
                        || refund.getStatus() == request.getStatus())
                .filter(refund -> from == null || !toInstant(refund.getCreatedAt()).isBefore(from))
                .filter(refund -> to == null || toInstant(refund.getCreatedAt()).isBefore(to))
                .filter(refund -> matches(refund, query)).toList();

        int page = request.getPage();
        int pageSize = request.getPageSize();
        long offset = (long) page * pageSize;
        int start = (int) Math.min(offset, matching.size());
        int end = Math.min(start + pageSize, matching.size());
        int totalPages = matching.isEmpty() ? 0 : (int) Math.ceil((double) matching.size() / pageSize);

        return ListRefundsResponse.newBuilder().addAllRefunds(matching.subList(start, end)).setPage(page)
                .setPageSize(pageSize).setTotalElements(matching.size()).setTotalPages(totalPages)
                .setHasNext(end < matching.size()).setHasPrevious(page > 0 && !matching.isEmpty()).build();
    }

    @Override
    public Refund get(GetRefundRequest request) {
        return REFUNDS.stream().filter(refund -> refund.getRefundId().equals(request.getRefundId())).findFirst()
                .orElseThrow(
                        () -> new AppException("Refund not found: " + request.getRefundId(), HttpStatus.NOT_FOUND));
    }

    private boolean matches(Refund refund, String query) {
        if (query.isBlank()) {
            return true;
        }
        NumberFormat amountFormat = NumberFormat.getNumberInstance(Locale.US);
        amountFormat.setMinimumFractionDigits(2);
        amountFormat.setMaximumFractionDigits(2);
        String displayAmount = "\u20a6" + amountFormat.format(new BigDecimal(refund.getAmount()));
        return refund.getAmount().toLowerCase(Locale.ROOT).contains(query)
                || displayAmount.toLowerCase(Locale.ROOT).contains(query)
                || refund.getCustomerName().toLowerCase(Locale.ROOT).contains(query)
                || refund.getCustomerEmail().toLowerCase(Locale.ROOT).contains(query);
    }

    private static Refund refund(String refundId, String transactionReference, String customerName,
            String customerEmail, String initiatedBy, String merchantName, String merchantEmail, String merchantId) {
        return Refund.newBuilder().setRefundId(refundId).setTransactionReference(transactionReference)
                .setCustomerName(customerName).setCustomerEmail(customerEmail).setAmount("15000.00")
                .setCurrencyCode("NGN").setStatus(RefundStatus.REFUND_STATUS_PROCESSED)
                .setCreatedAt(toTimestamp(CREATED_AT)).setRefundedAt(toTimestamp(REFUNDED_AT)).setRefundType("Full")
                .setRefundInitiatedBy(initiatedBy).setRefundChannel("Original Payment").setMerchantName(merchantName)
                .setMerchantEmail(merchantEmail).setMerchantId(merchantId).build();
    }

    private static Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    }

    private static Instant toInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}
