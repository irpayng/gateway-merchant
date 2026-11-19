package com.tms.gateway.modules.dashboard.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.tms.gateway.core.exception.AppException;
import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import java.math.BigDecimal;
import java.time.*;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-02T23:30:00Z"), ZoneOffset.UTC);
    private final GatewayMerchantUserDetails user = GatewayMerchantUserDetails.builder().id(4L).merchantId("m1")
            .emailVerifiedAt(Instant.EPOCH).build();
    private final DashboardService service = new DashboardService(new MockDashboardDataProvider(), clock);

    @Test
    void todayUsesLagosDateAndHourlyBucketsWithExactTotal() {
        var revenue = service.getDashboard(user, "today", null, null).revenue();
        assertEquals("2026-11-03", revenue.startDate());
        assertEquals(24, revenue.points().size());
        assertEquals("2026-11-03T00:00+01:00", revenue.points().getFirst().bucketStart());
        var sum = revenue.points().stream().map(p -> new BigDecimal(p.amount())).reduce(BigDecimal.ZERO,
                BigDecimal::add);
        assertEquals(0, sum.compareTo(new BigDecimal(revenue.total())));
    }

    @Test
    void presetsUseInclusiveRangesAndPayoutsDoNotChangeWithFilter() {
        var weekly = service.getDashboard(user, "last_7_days", null, null);
        assertEquals("2026-10-28", weekly.revenue().startDate());
        assertEquals(7, weekly.revenue().points().size());
        assertEquals(30, service.getDashboard(user, "last_30_days", null, null).revenue().points().size());
        var yearly = service.getDashboard(user, "one_year", null, null);
        assertEquals("2025-11-04", yearly.revenue().startDate());
        assertEquals("month", yearly.revenue().interval());
        assertEquals(weekly.lastPayout(), yearly.lastPayout());
        assertEquals(weekly.nextPayout(), yearly.nextPayout());
    }

    @Test
    void customDatesAreInclusiveAndMockIsRepeatable() {
        var first = service.getDashboard(user, "custom", "2026-10-01", "2026-10-03");
        assertEquals(3, first.revenue().points().size());
        assertEquals(first, service.getDashboard(user, "custom", "2026-10-01", "2026-10-03"));
    }

    @Test
    void invalidRangesFailBeforeCallingProvider() {
        var provider = mock(DashboardDataProvider.class);
        var checked = new DashboardService(provider, clock);
        assertThrows(AppException.class, () -> checked.getDashboard(user, "unknown", null, null));
        assertThrows(AppException.class, () -> checked.getDashboard(user, "custom", null, null));
        assertThrows(AppException.class, () -> checked.getDashboard(user, "custom", "2026-02-30", "2026-03-01"));
        assertThrows(AppException.class, () -> checked.getDashboard(user, "custom", "2026-11-02", "2026-11-01"));
        assertThrows(AppException.class, () -> checked.getDashboard(user, "custom", "2026-11-02", "2026-11-04"));
        assertThrows(AppException.class, () -> checked.getDashboard(user, "custom", "2020-01-01", "2026-11-02"));
        assertThrows(AppException.class, () -> checked.getDashboard(user, "today", "2026-11-02", null));
        verifyNoInteractions(provider);
    }

    @Test
    void merchantIsTakenFromPrincipalAndUnauthorizedRequestsAreRejected() {
        var provider = mock(DashboardDataProvider.class);
        var checked = new DashboardService(provider, clock);
        checked.getDashboard(user, "today", null, null);
        verify(provider).getDashboard(eq("m1"), any(), eq(clock.instant()));
        assertEquals(401, assertThrows(AppException.class, () -> checked.getDashboard(null, "today", null, null))
                .getStatus().value());
        var disabled = GatewayMerchantUserDetails.builder().merchantId("m1").build();
        assertEquals(403, assertThrows(AppException.class, () -> checked.getDashboard(disabled, "today", null, null))
                .getStatus().value());
        var unassigned = GatewayMerchantUserDetails.builder().emailVerifiedAt(Instant.EPOCH).build();
        assertEquals(403, assertThrows(AppException.class, () -> checked.getDashboard(unassigned, "today", null, null))
                .getStatus().value());
    }
}
