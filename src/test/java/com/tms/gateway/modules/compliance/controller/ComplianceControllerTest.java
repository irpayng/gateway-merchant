package com.tms.gateway.modules.compliance.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.tms.gateway.core.exception.GlobalExceptionHandler;
import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.modules.compliance.service.ComplianceMapper;
import com.tms.gateway.modules.compliance.service.ComplianceService;
import com.tms.gateway.modules.grpc.compliance.ComplianceData;
import com.tms.gateway.modules.grpc.service.ComplianceGrpcClient;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ComplianceControllerTest {
    private final ComplianceGrpcClient client = mock(ComplianceGrpcClient.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        var service = new ComplianceService(client, new ComplianceMapper());
        mvc = MockMvcBuilders.standaloneSetup(new ComplianceController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var user = GatewayMerchantUserDetails.builder().id(4L).merchantId("m1").emailVerifiedAt(Instant.now()).build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingMutationHeadersAreBadRequest() throws Exception {
        mvc.perform(put("/compliance/profile").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(client);
    }

    @Test
    void partialProfileReturnsVersionAndNoStore() throws Exception {
        when(client.saveProfile(any(), any()))
                .thenReturn(ComplianceData.newBuilder().setMerchantId("m1").setVersion(5).setStatus("draft").build());
        mvc.perform(put("/compliance/profile").header("Idempotency-Key", "profile-1").header("X-Expected-Version", "4")
                .contentType(MediaType.APPLICATION_JSON).content("{\"trading_name\":\"ACME\"}"))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"5\""))
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.data.version").value(5));
        verify(client).saveProfile(
                argThat(c -> c.getActorUserId() == 4 && c.getMerchantId().equals("m1") && c.getExpectedVersion() == 4),
                argThat(r -> "ACME".equals(r.tradingName())));
    }

    @Test
    void nullOwnerEntriesAreRejected() throws Exception {
        mvc.perform(put("/compliance/owners").header("Idempotency-Key", "owners-1").header("X-Expected-Version", "4")
                .contentType(MediaType.APPLICATION_JSON).content("{\"owners\":[null]}"))
                .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(client);
    }

    @Test
    void submissionWithoutAcceptanceIsRejected() throws Exception {
        mvc.perform(post("/compliance/submit").header("Idempotency-Key", "submit-1").header("X-Expected-Version", "4")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(client);
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mvc.perform(put("/compliance/profile").header("Idempotency-Key", "profile-1").header("X-Expected-Version", "4")
                .contentType(MediaType.APPLICATION_JSON).content("{broken")).andExpect(status().isBadRequest());
        verifyNoInteractions(client);
    }
}
