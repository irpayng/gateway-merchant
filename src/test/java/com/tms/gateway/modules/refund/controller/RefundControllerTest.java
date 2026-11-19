package com.tms.gateway.modules.refund.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tms.gateway.core.dto.PagedResponse;
import com.tms.gateway.core.exception.GlobalExceptionHandler;
import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.modules.refund.service.RefundService;
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

class RefundControllerTest {

    private final RefundService service = org.mockito.Mockito.mock(RefundService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new RefundController(service))
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
    void listForwardsAuthenticatedUserAndQueryFilters() throws Exception {
        Instant from = Instant.parse("2025-07-15T00:00:00Z");
        Instant to = Instant.parse("2025-07-16T00:00:00Z");
        when(service.list(42L, "john", "processed", from, to, 1, 10))
                .thenReturn(PagedResponse.from(List.of(), 0, 1, 10, "/refunds"));

        mvc.perform(get("/refunds").param("search", "john").param("status", "processed")
                .param("start_date", from.toString()).param("end_date", to.toString()).param("page", "1")
                .param("size", "10")).andExpect(status().isOk()).andExpect(jsonPath("$.meta.current_page").value(1))
                .andExpect(jsonPath("$.meta.per_page").value(10));

        verify(service).list(42L, "john", "processed", from, to, 1, 10);
    }

    @Test
    void listDefaultsToFirstPage() throws Exception {
        when(service.list(42L, null, null, null, null, 1, 5))
                .thenReturn(PagedResponse.from(List.of(), 0, 1, 5, "/refunds"));

        mvc.perform(get("/refunds")).andExpect(status().isOk()).andExpect(jsonPath("$.meta.current_page").value(1))
                .andExpect(jsonPath("$.meta.per_page").value(5));

        verify(service).list(42L, null, null, null, null, 1, 5);
    }
}
