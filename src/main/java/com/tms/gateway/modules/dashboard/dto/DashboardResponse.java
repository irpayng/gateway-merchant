package com.tms.gateway.modules.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record DashboardResponse(String currency, String timezone, Revenue revenue,
        @JsonProperty("last_payout") LastPayout lastPayout, @JsonProperty("next_payout") NextPayout nextPayout) {
    public record Revenue(String period, @JsonProperty("start_date") String startDate,
            @JsonProperty("end_date") String endDate, String total, String interval, List<RevenuePoint> points) {
    }
    public record RevenuePoint(@JsonProperty("bucket_start") String bucketStart, String amount) {
    }
    public record LastPayout(String amount, @JsonProperty("paid_at") String paidAt) {
    }
    public record NextPayout(String amount, @JsonProperty("scheduled_at") String scheduledAt) {
    }
}
