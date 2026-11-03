package com.tms.gateway.modules.dashboard.service;

import com.tms.gateway.modules.dashboard.dto.DashboardResponse;
import java.time.Instant;

/**
 * Replace the mock implementation with authoritative revenue and settlement
 * reads.
 */
public interface DashboardDataProvider {
    DashboardResponse getDashboard(String merchantId, DashboardQuery query, Instant asOf);
}
