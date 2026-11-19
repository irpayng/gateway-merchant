package com.tms.gateway.modules.dashboard.service;

import com.tms.gateway.modules.dashboard.dto.DashboardResponse;
import com.tms.gateway.modules.dashboard.dto.DashboardResponse.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Deterministic synthetic data until transaction and settlement integrations
 * are available.
 */
@Service
public class MockDashboardDataProvider implements DashboardDataProvider {
    private static final ZoneId ZONE = ZoneId.of("Africa/Lagos");

    @Override
    public DashboardResponse getDashboard(String merchantId, DashboardQuery query, Instant asOf) {
        var points = new ArrayList<RevenuePoint>();
        var cursor = query.startDate().atStartOfDay(ZONE);
        var end = query.endDate().plusDays(1).atStartOfDay(ZONE);
        var total = new BigDecimal("0.00");
        while (cursor.isBefore(end)) {
            var next = switch (query.interval()) {
                case "hour" -> cursor.plusHours(1);
                case "month" -> cursor.withDayOfMonth(1).plusMonths(1);
                default -> cursor.plusDays(1);
            };
            var bucketEnd = next.isAfter(end) ? end : next;
            var amount = new BigDecimal("0.00");
            // Aggregate the same synthetic hourly collections for every filter.
            for (var hour = cursor; hour.isBefore(bucketEnd)
                    && hour.toInstant().isBefore(asOf); hour = hour.plusHours(1)) {
                amount = amount.add(BigDecimal
                        .valueOf(Math.floorMod((merchantId + hour.toInstant()).hashCode(), 900000) + 10000, 2));
            }
            points.add(new RevenuePoint(cursor.toOffsetDateTime().toString(), amount.toPlainString()));
            total = total.add(amount);
            cursor = next;
        }
        var today = asOf.atZone(ZONE).toLocalDate();
        return new DashboardResponse("NGN", ZONE.getId(),
                new Revenue(query.period(), query.startDate().toString(), query.endDate().toString(),
                        total.toPlainString(), query.interval(), List.copyOf(points)),
                new LastPayout("1930000.23",
                        today.minusDays(1).atTime(17, 0).atZone(ZONE).toOffsetDateTime().toString()),
                new NextPayout("3200000.23",
                        today.plusDays(1).atTime(17, 0).atZone(ZONE).toOffsetDateTime().toString()));
    }
}
