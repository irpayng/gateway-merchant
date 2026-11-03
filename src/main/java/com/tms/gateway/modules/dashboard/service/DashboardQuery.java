package com.tms.gateway.modules.dashboard.service;

import java.time.LocalDate;

/** Inclusive local dates; providers must use Africa/Lagos bucket boundaries. */
public record DashboardQuery(String period, LocalDate startDate, LocalDate endDate, String interval) {
}
