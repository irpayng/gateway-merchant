package com.tms.gateway.modules.customer.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.tms.gateway.core.exception.GlobalExceptionHandler;
import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.modules.customer.dto.*;
import com.tms.gateway.modules.customer.service.CustomerService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CustomerControllerTest {
    private final CustomerService service = mock(CustomerService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new CustomerController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var user = GatewayMerchantUserDetails.builder().id(4L).merchantId("m1").email("merchant@example.com")
                .emailVerifiedAt(Instant.now()).build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listUsesSearchAndZeroBasedPagination() throws Exception {
        when(service.list("m1", "jane", 0, 20))
                .thenReturn(new CustomerListResponse(List.of(), 0, 20, 0, 0, false, false));

        mvc.perform(get("/customers").param("search", "jane").param("page", "0").param("size", "20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20));

        verify(service).list("m1", "jane", 0, 20);
    }

    @Test
    void createAcceptsOnlyTheScreenFieldsAndOptionalE164Phone() throws Exception {
        when(service.create(eq("m1"), any())).thenReturn(new CustomerResponse("ref-1", "id-1", "jane@example.com",
                "Jane Doe", null, Instant.parse("2025-01-01T00:00:00Z"), false));

        mvc.perform(post("/customers").contentType(MediaType.APPLICATION_JSON)
                .content("{\"first_name\":\"Jane\",\"last_name\":\"Doe\",\"email\":\"jane@example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.customer_ref").value("ref-1"))
                .andExpect(jsonPath("$.data.phone_number").value(org.hamcrest.Matchers.nullValue()));

        verify(service).create(eq("m1"), argThat(request -> "Jane".equals(request.firstName())
                && "jane@example.com".equals(request.email()) && request.phoneNumber() == null));
    }

    @Test
    void createRejectsInvalidInternationalPhoneNumber() throws Exception {
        mvc.perform(post("/customers").contentType(MediaType.APPLICATION_JSON)
                .content("{\"first_name\":\"Jane\",\"last_name\":\"Doe\",\"email\":\"jane@example.com\","
                        + "\"phone_number\":\"08012345678\"}"))
                .andExpect(status().isUnprocessableEntity());

        verifyNoInteractions(service);
    }

    @Test
    void getCustomerDetailsReturnsSummary() throws Exception {
        when(service.get("m1", "ref-1")).thenReturn(new CustomerDetailsResponse("ref-1", null, "jane@example.com",
                "Jane Doe", null, Instant.parse("2025-01-01T00:00:00Z"), false, new BigDecimal("25.00"), "NGN", 1, 2));

        mvc.perform(get("/customers/ref-1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_spend").value(25.0))
                .andExpect(jsonPath("$.data.successful_count").value(1))
                .andExpect(jsonPath("$.data.total_count").value(2));
    }

    @Test
    void blacklistEndpointAcceptsBooleanState() throws Exception {
        when(service.setBlacklisted("m1", "ref-1", true)).thenReturn(new CustomerResponse("ref-1", null,
                "jane@example.com", null, null, Instant.parse("2025-01-01T00:00:00Z"), true));

        mvc.perform(patch("/customers/ref-1/blacklist").contentType(MediaType.APPLICATION_JSON)
                .content("{\"blacklisted\":true}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blacklisted").value(true));

        verify(service).setBlacklisted("m1", "ref-1", true);
    }

    @Test
    void customerTransactionsHaveSeparatePagination() throws Exception {
        when(service.transactions("m1", "ref-1", 1, 10))
                .thenReturn(new CustomerTransactionPageResponse(List.of(), 1, 10, 0, 0, false, false));

        mvc.perform(get("/customers/ref-1/transactions").param("page", "1").param("size", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(10));

        verify(service).transactions("m1", "ref-1", 1, 10);
    }
}
