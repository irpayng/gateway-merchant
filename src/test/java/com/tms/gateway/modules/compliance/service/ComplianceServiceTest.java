package com.tms.gateway.modules.compliance.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.tms.gateway.core.exception.AppException;
import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.modules.compliance.dto.SubmitComplianceRequest;
import com.tms.gateway.modules.grpc.compliance.*;
import com.tms.gateway.modules.grpc.service.ComplianceGrpcClient;
import java.time.Instant;
import org.junit.jupiter.api.*;
class ComplianceServiceTest {
    private final ComplianceGrpcClient client = mock(ComplianceGrpcClient.class);
    private final ComplianceService service = new ComplianceService(client, new ComplianceMapper());
    private GatewayMerchantUserDetails user(long id, String merchant) {
        return GatewayMerchantUserDetails.builder().id(id).merchantId(merchant).emailVerifiedAt(Instant.now()).build();
    }
    @Test
    void multipleUsersShareMerchantApplicationAndRetainActor() {
        var a = service.mutation(user(1, "m1"), "save-a", 4);
        var b = service.mutation(user(2, "m1"), "save-b", 4);
        assertEquals(a.getMerchantId(), b.getMerchantId());
        assertNotEquals(a.getActorUserId(), b.getActorUserId());
    }
    @Test
    void readsAlwaysUseSourceOfTruth() {
        var c = service.context(user(1, "m1"), null, null);
        when(client.getCompliance(c))
                .thenReturn(ComplianceData.newBuilder().setMerchantId("m1").setVersion(9).setStatus("pending").build());
        assertEquals(9, service.getCompliance(c).version());
        verify(client).getCompliance(c);
    }
    @Test
    void mismatchedMerchantResponseIsRejected() {
        var c = service.context(user(1, "m1"), null, null);
        when(client.getCompliance(c)).thenReturn(ComplianceData.newBuilder().setMerchantId("m2").build());
        assertThrows(AppException.class, () -> service.getCompliance(c));
    }
    @Test
    void blockedUserCannotMutate() {
        var blocked = GatewayMerchantUserDetails.builder().id(1L).merchantId("m1").emailVerifiedAt(Instant.now())
                .blockedAt(Instant.now()).build();
        assertThrows(AppException.class, () -> service.mutation(blocked, "key", 1));
        verifyNoInteractions(client);
    }
    @Test
    void mutationsRequireReplayKeyAndNonnegativeVersion() {
        assertThrows(AppException.class, () -> service.mutation(user(1, "m1"), "", 1));
        assertThrows(AppException.class, () -> service.mutation(user(1, "m1"), "key", -1));
    }
    @Test
    void acceptanceAndSubmissionUseSingleAtomicCall() {
        var c = service.mutation(user(1, "m1"), "submit-1", 6);
        when(client.submitCompliance(c, true, "127.0.0.1", "browser"))
                .thenReturn(ComplianceData.newBuilder().setMerchantId("m1").setStatus("pending").build());
        assertEquals("pending",
                service.submitCompliance(c, new SubmitComplianceRequest(true), "127.0.0.1", "browser").status());
        verify(client).submitCompliance(c, true, "127.0.0.1", "browser");
        verifyNoMoreInteractions(client);
    }
    @Test
    void missingAcceptanceNeverCallsKyc() {
        var c = service.mutation(user(1, "m1"), "submit-1", 6);
        assertThrows(AppException.class,
                () -> service.submitCompliance(c, new SubmitComplianceRequest(false), "ip", "ua"));
        verifyNoInteractions(client);
    }
}
