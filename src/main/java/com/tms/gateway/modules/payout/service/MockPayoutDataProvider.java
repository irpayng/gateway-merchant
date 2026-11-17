package com.tms.gateway.modules.payout.service;

import com.tms.gateway.core.exception.AppException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class MockPayoutDataProvider implements PayoutDataProvider {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Africa/Lagos");
    private static final Instant SALES_DATE = LocalDateTime.of(2025, 3, 14, 8, 20).atZone(BUSINESS_ZONE).toInstant();
    private static final Instant SETTLED_AT = LocalDateTime.of(2025, 3, 15, 8, 20).atZone(BUSINESS_ZONE).toInstant();
    private static final List<PayoutRecord> PAYOUTS = createPayouts();

    @Override
    public List<PayoutRecord> list(Long userId) {
        return PAYOUTS;
    }

    @Override
    public PayoutRecord get(Long userId, String payoutId) {
        return PAYOUTS.stream()
                .filter(payout -> payout.payoutId().equals(payoutId)
                        || payout.paymentReference().equals(payoutId))
                .findFirst()
                .orElseThrow(() -> new AppException("Payout not found: " + payoutId, HttpStatus.NOT_FOUND));
    }

    private static List<PayoutRecord> createPayouts() {
        List<PayoutRecord> payouts = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            String suffix = String.format(Locale.ROOT, "%03d", i);
            String status = i <= 5 || i > 15 ? "completed" : i <= 10 ? "pending" : "failed";
            Instant settledAt = status.equals("completed") ? SETTLED_AT : null;
            payouts.add(new PayoutRecord("payout-" + suffix,
                    i == 1 ? "b5e4b374-5cb1-4138-8dff-fdd2d3de8ca9" : "309022002909209" + suffix,
                    new BigDecimal("15000.00"), new BigDecimal("15000.00"), "NGN", "Access Bank", "John Doe",
                    "0029299292", status, SALES_DATE, settledAt));
        }
        return List.copyOf(payouts);
    }
}
