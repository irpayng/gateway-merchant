package com.tms.gateway.modules.payout.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tms.gateway.core.dto.PagedResponse;
import com.tms.gateway.core.exception.GlobalExceptionHandler;
import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.modules.grpc.service.ConfigGrpcClient;
import com.tms.gateway.modules.payout.dto.PayoutDetailDto;
import com.tms.gateway.modules.payout.dto.PayoutListItemDto;
import com.tms.gateway.modules.payout.service.PayoutHistoryService;
import com.tms.gateway.modules.payout.service.PayoutService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PayoutControllerTest {

    private final PayoutHistoryService historyService = org.mockito.Mockito.mock(PayoutHistoryService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        var controller = new PayoutController(org.mockito.Mockito.mock(PayoutService.class), historyService,
                org.mockito.Mockito.mock(ConfigGrpcClient.class));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var user = GatewayMerchantUserDetails.builder().id(42L).merchantId("m1").email("merchant@example.com")
                .emailVerifiedAt(Instant.now()).build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listDefaultsToFiveRowsAndForwardsFilters() throws Exception {
        String from = "14/03/2025";
        String to = "15/03/2025";
        var item = new PayoutListItemDto("payout-001", "reference-001", new BigDecimal("15000"),
                new BigDecimal("15000"), "NGN", "Access Bank", "completed", "Completed",
                Instant.parse("2025-03-14T07:20:00Z"), Instant.parse("2025-03-15T07:20:00Z"));
        when(historyService.list(42L, "access", "completed", from, to, 1, 5))
                .thenReturn(PagedResponse.from(List.of(item), 1, 1, 5, "/payouts"));

        mvc.perform(get("/payouts").param("search", "access").param("status", "completed")
                .param("start_date", from).param("end_date", to))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].payment_reference").value("reference-001"))
                .andExpect(jsonPath("$.data[0].total_value").value(15000))
                .andExpect(jsonPath("$.data[0].receiving_bank").value("Access Bank"))
                .andExpect(jsonPath("$.data[0].date_time_settled").isString())
                .andExpect(jsonPath("$.meta.per_page").value(5));

        verify(historyService).list(42L, "access", "completed", from, to, 1, 5);
    }

    @Test
    void detailReturnsPayoutEnvelope() throws Exception {
        when(historyService.getDetail(42L, "payout-001")).thenReturn(
                new PayoutDetailDto("payout-001", new BigDecimal("15000"), "NGN", "completed", "Completed",
                        "reference-001", new BigDecimal("15000"), new BigDecimal("15000"),
                        Instant.parse("2025-03-15T07:20:00Z"),
                        new PayoutDetailDto.ReceivingBank("Access Bank", "John Doe", "0029299292")));

        mvc.perform(get("/payouts/payout-001")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payment_reference").value("reference-001"))
                .andExpect(jsonPath("$.data.receiving_bank.bank_name").value("Access Bank"))
                .andExpect(jsonPath("$.data.receiving_bank.account_number").value("0029299292"));
    }

    @Test
    void exportDownloadsFilteredCsv() throws Exception {
        when(historyService.exportCsv(42L, null, "completed", null, null))
                .thenReturn("\"Payment Reference\",\"Total Value\"\r\n");

        mvc.perform(get("/payouts/export").param("status", "completed"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"payouts.csv\""))
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(content().string("\"Payment Reference\",\"Total Value\"\r\n"));

        verify(historyService).exportCsv(42L, null, "completed", null, null);
    }
}
