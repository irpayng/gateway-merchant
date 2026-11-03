package com.tms.gateway.modules.dashboard.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.tms.gateway.core.exception.GlobalExceptionHandler;
import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.modules.dashboard.service.DashboardService;
import com.tms.gateway.modules.dashboard.service.MockDashboardDataProvider;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DashboardControllerTest {
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders
                .standaloneSetup(new DashboardController(new DashboardService(new MockDashboardDataProvider())))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var user = GatewayMerchantUserDetails.builder().id(4L).merchantId("m1").emailVerifiedAt(Instant.EPOCH).build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void defaultDashboardMatchesDesignContract() throws Exception {
        mvc.perform(get("/dashboard")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.currency").value("NGN"))
                .andExpect(jsonPath("$.data.timezone").value("Africa/Lagos"))
                .andExpect(jsonPath("$.data.revenue.period").value("today"))
                .andExpect(jsonPath("$.data.revenue.points.length()").value(24))
                .andExpect(jsonPath("$.data.revenue.points[0].bucket_start").isString())
                .andExpect(jsonPath("$.data.revenue.total").isString())
                .andExpect(jsonPath("$.data.last_payout.paid_at").isString())
                .andExpect(jsonPath("$.data.next_payout.scheduled_at").isString());
    }

    @Test
    void invalidFilterReturnsStandardError() throws Exception {
        mvc.perform(get("/dashboard").param("period", "custom")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void customRangeReturnsOnePointPerDay() throws Exception {
        mvc.perform(get("/dashboard").param("period", "custom").param("start_date", "2026-01-01").param("end_date",
                "2026-01-07")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revenue.start_date").value("2026-01-01"))
                .andExpect(jsonPath("$.data.revenue.points.length()").value(7));
    }

    @Test
    void missingPrincipalIsUnauthorized() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(get("/dashboard")).andExpect(status().isUnauthorized());
    }
}
