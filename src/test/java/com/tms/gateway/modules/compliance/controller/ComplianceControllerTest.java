package com.tms.gateway.modules.compliance.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tms.gateway.core.exception.AppException;
import com.tms.gateway.core.exception.GlobalExceptionHandler;
import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.modules.compliance.service.ComplianceMapper;
import com.tms.gateway.modules.compliance.service.ComplianceService;
import com.tms.gateway.modules.grpc.compliance.ComplianceData;
import com.tms.gateway.modules.grpc.service.ComplianceGrpcClient;
import jakarta.validation.Validation;
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
        var objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE);
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        mvc = MockMvcBuilders.standaloneSetup(new ComplianceController(service, objectMapper, validator))
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
    void registeredBusinessDetailsAreSavedAsOneScreenWithAmountOnly() throws Exception {
        when(client.saveProfile(any(), any()))
                .thenReturn(ComplianceData.newBuilder().setMerchantId("m1").setVersion(6).setStatus("draft").build());
        when(client.saveContactWithFile(any(), any(), isNull()))
                .thenReturn(ComplianceData.newBuilder().setMerchantId("m1").setVersion(7).setStatus("draft").build());

        mvc.perform(put("/compliance/business/details").header("Idempotency-Key", "business-details-1")
                .header("X-Expected-Version", "5").contentType(MediaType.APPLICATION_JSON).content("""
                        {
                          "country": "NG",
                          "industry": "technology",
                          "category": "software",
                          "trading_name": "Acme",
                          "sales_volume": "250000.00",
                          "business_description": "Digital services",
                          "business_type": "registered",
                          "support_email": "support@example.com",
                          "dispute_email": "disputes@example.com",
                          "same_as_support_email": false,
                          "phone": "+2348012345678",
                          "address": "1 Main Street",
                          "state": "Lagos",
                          "lga": "ikeja",
                          "online_links": [{"platform": "website", "url": "https://example.com"}]
                        }
                        """)).andExpect(status().isOk()).andExpect(header().string("ETag", "\"7\""))
                .andExpect(jsonPath("$.data.version").value(7));

        verify(client).saveProfile(argThat(context -> context.getExpectedVersion() == 5),
                argThat(request -> "registered".equals(request.businessType())
                        && "250000.00".equals(request.salesVolume())
                        && "Digital services".equals(request.businessDescription())));
        verify(client).saveContactWithFile(
                argThat(context -> context.getActorUserId() == 4 && context.getMerchantId().equals("m1")
                        && context.getExpectedVersion() == 6),
                argThat(request -> "support@example.com".equals(request.supportEmail())
                        && "Lagos".equals(request.state()) && "ikeja".equals(request.lga())
                        && request.onlineLinks().size() == 1),
                isNull());
    }

    @Test
    void contactEndpointForwardsStateAndLga() throws Exception {
        when(client.saveContact(any(), any())).thenReturn(ComplianceData.newBuilder().setMerchantId("m1").setVersion(6)
                .setStatus("draft").setContact(com.tms.gateway.modules.grpc.compliance.ContactData.newBuilder()
                        .setState("Lagos").setLga("ikeja"))
                .build());

        mvc.perform(
                put("/compliance/contact").header("Idempotency-Key", "contact-state").header("X-Expected-Version", "5")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"state\":\"Lagos\",\"lga\":\"ikeja\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.contact.state").value("Lagos"))
                .andExpect(jsonPath("$.data.contact.lga").value("ikeja"));

        verify(client).saveContact(any(),
                argThat(request -> "Lagos".equals(request.state()) && "ikeja".equals(request.lga())));
    }

    @Test
    void ownersEndpointForwardsStateAndLga() throws Exception {
        when(client.saveOwners(any(), any())).thenReturn(ComplianceData.newBuilder().setMerchantId("m1").setVersion(6)
                .setStatus("draft").addOwners(com.tms.gateway.modules.grpc.compliance.OwnerData.newBuilder()
                        .setState("Lagos").setLga("ikeja"))
                .build());

        mvc.perform(put("/compliance/owners").header("Idempotency-Key", "owner-state").header("X-Expected-Version", "5")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"owners\":[{\"state\":\"Lagos\",\"lga\":\"ikeja\"}]}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.owners[0].state").value("Lagos"))
                .andExpect(jsonPath("$.data.owners[0].lga").value("ikeja"));

        verify(client).saveOwners(any(), argThat(owners -> owners.size() == 1
                && "Lagos".equals(owners.getFirst().state()) && "ikeja".equals(owners.getFirst().lga())));
    }

    @Test
    void registeredBusinessDetailsReportPartialSaveIfSecondExistingRpcFails() throws Exception {
        when(client.saveProfile(any(), any()))
                .thenReturn(ComplianceData.newBuilder().setMerchantId("m1").setVersion(6).setStatus("draft").build());
        when(client.saveContactWithFile(any(), any(), isNull()))
                .thenThrow(new AppException("Contact service conflict", org.springframework.http.HttpStatus.CONFLICT));

        mvc.perform(put("/compliance/business/details").header("Idempotency-Key", "business-details-partial")
                .header("X-Expected-Version", "5").contentType(MediaType.APPLICATION_JSON)
                .content("{\"trading_name\":\"Acme\",\"support_email\":\"support@example.com\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.partial_save").value(true))
                .andExpect(jsonPath("$.saved_version").value(6))
                .andExpect(jsonPath("$.saved_sections[0]").value("profile"));
    }

    @Test
    void registeredBusinessRegistrationEndpointAcceptsOnlyRegistrationFields() throws Exception {
        when(client.saveRegistration(any(), any()))
                .thenReturn(ComplianceData.newBuilder().setMerchantId("m1").setVersion(7).setStatus("draft").build());

        mvc.perform(put("/compliance/business/registration").header("Idempotency-Key", "registration-1")
                .header("X-Expected-Version", "6").contentType(MediaType.APPLICATION_JSON)
                .content("{\"registration_number\":\"RC1234\",\"tax_identification_number\":\"1234567-1234\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(7));

        verify(client).saveRegistration(any(),
                argThat(request -> request.businessType() == null && "RC1234".equals(request.registrationNumber())
                        && "1234567-1234".equals(request.taxIdentificationNumber())));
    }

    @Test
    void registeredShareholdersEndpointAcceptsTheScreenFields() throws Exception {
        when(client.saveOwners(any(), any()))
                .thenReturn(ComplianceData.newBuilder().setMerchantId("m1").setVersion(8).setStatus("draft").build());

        mvc.perform(put("/compliance/business/shareholders").header("Idempotency-Key", "shareholders-1")
                .header("X-Expected-Version", "7").contentType(MediaType.APPLICATION_JSON).content("""
                        {
                          "shareholders": [{
                            "full_name": "Jane Doe",
                            "date_of_birth": "15-04-1980",
                            "nationality": "Nigerian",
                            "role": "director",
                            "id_document_type": "national_id",
                            "id_document_id": "doc-id",
                            "address": "2 Home Street",
                            "same_as_business_address": false,
                            "state": "Lagos",
                            "lga": "ikeja",
                            "proof_of_address_document_id": "doc-address"
                          }]
                        }
                        """)).andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(8));

        verify(client).saveOwners(any(),
                argThat(shareholders -> shareholders.size() == 1
                        && "Jane Doe".equals(shareholders.getFirst().fullName())
                        && "1980-04-15".equals(shareholders.getFirst().dateOfBirth())
                        && "Lagos".equals(shareholders.getFirst().state())));
    }

    @Test
    void registeredBusinessSalesVolumeRejectsCurrencyText() throws Exception {
        mvc.perform(put("/compliance/business/details").header("Idempotency-Key", "business-details-2")
                .header("X-Expected-Version", "5").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sales_volume\":\"NGN 250,000\"}")).andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(client);
    }

    @Test
    void registeredBusinessDetailsRejectEmptyPatch() throws Exception {
        mvc.perform(put("/compliance/business/details").header("Idempotency-Key", "business-details-empty")
                .header("X-Expected-Version", "5").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(client);
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

    @Test
    void schemaIncludesStateAndLgaForContactAndOwnerSections() throws Exception {
        mvc.perform(get("/compliance/schema").param("business_type", "starter")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps[1].fields[6].name").value("state"))
                .andExpect(jsonPath("$.data.steps[1].fields[6].options_ref").value("states"))
                .andExpect(jsonPath("$.data.steps[1].fields[7].name").value("lga"))
                .andExpect(jsonPath("$.data.steps[2].fields[6].name").value("state"))
                .andExpect(jsonPath("$.data.steps[2].fields[6].options_ref").value("states"))
                .andExpect(jsonPath("$.data.steps[2].fields[7].name").value("lga"));

        mvc.perform(get("/compliance/schema").param("business_type", "registered")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps[0].fields[12].name").value("state"))
                .andExpect(jsonPath("$.data.steps[0].fields[12].options_ref").value("states"))
                .andExpect(jsonPath("$.data.steps[0].fields[13].name").value("lga"))
                .andExpect(jsonPath("$.data.steps[2].fields[7].name").value("state"))
                .andExpect(jsonPath("$.data.steps[2].fields[7].options_ref").value("states"))
                .andExpect(jsonPath("$.data.steps[2].fields[8].name").value("lga"));
    }
}
