package com.tms.gateway.modules.dashboard.service;

import com.tms.gateway.core.exception.AppException;
import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.modules.dashboard.dto.DashboardResponse;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
    private static final ZoneId ZONE = ZoneId.of("Africa/Lagos");
    private final DashboardDataProvider provider;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public DashboardService(DashboardDataProvider provider) {
        this(provider, Clock.systemUTC());
    }

    DashboardService(DashboardDataProvider provider, Clock clock) {
        this.provider = provider;
        this.clock = clock;
    }

    public DashboardResponse getDashboard(GatewayMerchantUserDetails user, String period, String startDate,
            String endDate) {
        if (user == null)
            throw new AppException("Authentication required", HttpStatus.UNAUTHORIZED);
        if (!user.isEnabled())
            throw new AppException("Account is disabled or not verified", HttpStatus.FORBIDDEN);
        if (user.getMerchantId() == null || user.getMerchantId().isBlank())
            throw new AppException("Merchant ID is not assigned", HttpStatus.FORBIDDEN);
        var now = clock.instant();
        var today = now.atZone(ZONE).toLocalDate();
        LocalDate start;
        LocalDate end = today;
        if (!"custom".equals(period) && (startDate != null || endDate != null))
            throw new AppException("start_date and end_date are only allowed for custom periods");
        start = switch (period) {
            case "today" -> today;
            case "last_7_days" -> today.minusDays(6);
            case "last_30_days" -> today.minusDays(29);
            case "one_year" -> today.minusYears(1).plusDays(1);
            case "custom" -> parseDate(startDate, "start_date");
            default -> throw new AppException("Use today, last_7_days, last_30_days, one_year or custom");
        };
        if ("custom".equals(period))
            end = parseDate(endDate, "end_date");
        if (start.isAfter(end))
            throw new AppException("start_date must not be after end_date");
        if (end.isAfter(today))
            throw new AppException("Future revenue dates are not allowed");
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > 366)
            throw new AppException("The revenue range must not exceed 366 days");
        String interval = days == 1 ? "hour" : days <= 31 ? "day" : "month";
        return provider.getDashboard(user.getMerchantId(), new DashboardQuery(period, start, end, interval), now);
    }

    private LocalDate parseDate(String value, String field) {
        if (value == null || value.isBlank())
            throw new AppException(field + " is required for custom periods");
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new AppException(field + " must use YYYY-MM-DD");
        }
    }
}
