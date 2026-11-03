package com.tms.gateway.modules.dashboard.controller;

import com.tms.gateway.core.dto.ApiResponse;
import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.modules.dashboard.dto.DashboardResponse;
import com.tms.gateway.modules.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/dashboard")
@Tag(name = "Dashboard", description = "Merchant home dashboard")
public class DashboardController {
    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Get home dashboard", description = "Currently returns mock revenue and settlement data. "
            + "Periods: today (default), last_7_days, last_30_days, one_year, custom. "
            + "Custom dates use YYYY-MM-DD and are inclusive in Africa/Lagos. Payout cards are independent of the revenue period.")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(
            @AuthenticationPrincipal GatewayMerchantUserDetails user,
            @RequestParam(name = "period", defaultValue = "today") String period,
            @RequestParam(name = "start_date", required = false) String startDate,
            @RequestParam(name = "end_date", required = false) String endDate) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(
                ApiResponse.success(service.getDashboard(user, period, startDate, endDate), "Dashboard retrieved"));
    }
}
