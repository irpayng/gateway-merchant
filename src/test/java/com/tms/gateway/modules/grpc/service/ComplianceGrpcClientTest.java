package com.tms.gateway.modules.grpc.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tms.gateway.core.exception.AppException;
import com.tms.gateway.modules.compliance.service.ComplianceValidationException;
import com.tms.gateway.modules.grpc.compliance.*;
import com.tms.gateway.modules.grpc.config.GrpcProperties;
import io.grpc.Status;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
class ComplianceGrpcClientTest {
    private final PgMerchantComplianceServiceGrpc.PgMerchantComplianceServiceBlockingStub stub = mock(
            PgMerchantComplianceServiceGrpc.PgMerchantComplianceServiceBlockingStub.class);
    private final ComplianceGrpcClient client = new ComplianceGrpcClient(new GrpcProperties());
    private final MutationContext context = MutationContext.newBuilder().setActorUserId(4).setMerchantId("m1")
            .setExpectedVersion(8).setIdempotencyKey("request-1").build();
    private ComplianceResponse success() {
        return ComplianceResponse.newBuilder().setSuccess(true)
                .setCompliance(ComplianceData.newBuilder().setMerchantId("m1")).build();
    }
    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(client, "stub", stub);
        when(stub.withDeadlineAfter(30, TimeUnit.SECONDS)).thenReturn(stub);
    }
    @Test
    void partialDraftForwardsPresenceAndMutationContext() throws Exception {
        when(stub.saveMerchantProfileDraft(any())).thenReturn(success());
        var r = new ObjectMapper().readValue("{\"trading_name\":\"ACME\"}",
                com.tms.gateway.modules.compliance.dto.SaveProfileRequest.class);
        client.saveProfile(context, r);
        var captor = ArgumentCaptor.forClass(MerchantProfileDraftRequest.class);
        verify(stub).saveMerchantProfileDraft(captor.capture());
        assertEquals(java.util.List.of("trading_name"), captor.getValue().getSuppliedFieldsList());
        assertEquals(context, captor.getValue().getContext());
        assertEquals("ACME", captor.getValue().getData().getTradingName());
    }
    @Test
    void explicitFalseIsNotTreatedAsOmitted() throws Exception {
        when(stub.saveMerchantContactDraft(any())).thenReturn(success());
        var r = new ObjectMapper().readValue("{\"same_as_support_email\":false}",
                com.tms.gateway.modules.compliance.dto.SaveContactRequest.class);
        client.saveContact(context, r);
        var captor = ArgumentCaptor.forClass(MerchantContactDraftRequest.class);
        verify(stub).saveMerchantContactDraft(captor.capture());
        assertEquals(java.util.List.of("same_as_support_email"), captor.getValue().getSuppliedFieldsList());
    }
    @Test
    void serverWithoutV2FailsClosed() {
        when(stub.startMerchantCompliance(any())).thenThrow(Status.UNIMPLEMENTED.asRuntimeException());
        assertEquals(503, assertThrows(AppException.class, () -> client.startCompliance(context)).getStatus().value());
    }
    @Test
    void versionConflictIsHttp409() {
        when(stub.acceptAgreementAndSubmit(any())).thenThrow(Status.ABORTED.asRuntimeException());
        assertEquals(409, assertThrows(AppException.class, () -> client.submitCompliance(context, true, "ip", "ua"))
                .getStatus().value());
    }
    @Test
    void submissionRetainsFieldErrors() {
        when(stub.acceptAgreementAndSubmit(any()))
                .thenReturn(
                        ComplianceResponse
                                .newBuilder().setErrorCode("VALIDATION_FAILED").addErrors(CorrectionData.newBuilder()
                                        .setStep("owners").setField("owners[0].full_name").setMessage("Required"))
                                .build());
        var error = assertThrows(ComplianceValidationException.class,
                () -> client.submitCompliance(context, true, "ip", "ua"));
        assertEquals("owners[0].full_name", error.getErrors().getFirst().field());
    }
}
