package com.tms.gateway.modules.compliance.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.tms.gateway.core.exception.AppException;
import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.modules.compliance.dto.*;
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
    @Test
    void registeredDetailsReuseProfileAndContactWithAdvancedVersion() {
        var context = service.mutation(user(1, "m1"), "business-details", 5);
        var request = new RegisteredBusinessDetailsRequest("NG", "technology", "software", "Acme", "250000.00",
                "Digital services", "registered", "support@example.com", "disputes@example.com", false,
                "+2348012345678", "1 Main Street", "ikeja", "doc-1",
                java.util.List.of(new OnlineLink("website", "https://example.com")));
        var profile = ComplianceData.newBuilder().setMerchantId("m1").setVersion(6).setStatus("draft").build();
        var contact = ComplianceData.newBuilder().setMerchantId("m1").setVersion(7).setStatus("draft").build();
        when(client.saveProfile(eq(context), any())).thenReturn(profile);
        when(client.saveContact(argThat(next -> next.getExpectedVersion() == 6), any())).thenReturn(contact);

        var response = service.saveRegisteredBusinessDetails(context, request);

        assertEquals(7, response.version());
        verify(client).saveProfile(eq(context),
                argThat(profileRequest -> "250000.00".equals(profileRequest.salesVolume())
                        && "registered".equals(profileRequest.businessType())));
        verify(client).saveContact(
                argThat(next -> next.getActorUserId() == 1 && next.getMerchantId().equals("m1")
                        && next.getIdempotencyKey().equals("business-details") && next.getExpectedVersion() == 6),
                argThat(contactRequest -> "support@example.com".equals(contactRequest.supportEmail())
                        && Boolean.FALSE.equals(contactRequest.sameAsSupportEmail())
                        && contactRequest.onlineLinks().size() == 1));
    }
    @Test
    void registeredDetailsWithOnlyContactFieldsSkipProfileWrite() {
        var context = service.mutation(user(1, "m1"), "contact-only", 5);
        var request = new RegisteredBusinessDetailsRequest(null, null, null, null, null, null, null,
                "support@example.com", null, null, null, null, null, null, null);
        when(client.saveContact(eq(context), any()))
                .thenReturn(ComplianceData.newBuilder().setMerchantId("m1").setVersion(6).setStatus("draft").build());

        assertEquals(6, service.saveRegisteredBusinessDetails(context, request).version());

        verify(client, never()).saveProfile(any(), any());
        verify(client).saveContact(eq(context), any());
    }
    @Test
    void registeredDetailsDoNotHideContactFailureAfterProfileWasSaved() {
        var context = service.mutation(user(1, "m1"), "business-partial", 5);
        var request = new RegisteredBusinessDetailsRequest("NG", null, null, null, null, null, null,
                "support@example.com", null, null, null, null, null, null, null);
        when(client.saveProfile(eq(context), any()))
                .thenReturn(ComplianceData.newBuilder().setMerchantId("m1").setVersion(6).setStatus("draft").build());
        when(client.saveContact(argThat(next -> next.getExpectedVersion() == 6), any()))
                .thenThrow(new AppException("Contact save failed", org.springframework.http.HttpStatus.CONFLICT));

        var failure = assertThrows(CompliancePartialSaveException.class,
                () -> service.saveRegisteredBusinessDetails(context, request));

        assertEquals(409, failure.getStatus().value());
        assertEquals(6, failure.getSavedVersion());
        verify(client).saveProfile(eq(context), any());
        verify(client).saveContact(argThat(next -> next.getExpectedVersion() == 6), any());
    }
    @Test
    void registeredRegistrationUsesExistingDraftRpc() {
        var context = service.mutation(user(1, "m1"), "registration", 3);
        when(client.saveRegistration(eq(context), any()))
                .thenReturn(ComplianceData.newBuilder().setMerchantId("m1").setVersion(4).setStatus("draft").build());

        var result = service.saveRegisteredBusinessRegistration(context,
                new RegisteredBusinessRegistrationRequest("RC1234", "1234567-1234"));

        assertEquals(4, result.version());
        verify(client).saveRegistration(eq(context),
                argThat(request -> request.businessType() == null && "RC1234".equals(request.registrationNumber())
                        && "1234567-1234".equals(request.taxIdentificationNumber())));
    }
    @Test
    void registeredShareholdersUseExistingOwnersDraftRpc() {
        var context = service.mutation(user(1, "m1"), "shareholders", 3);
        var shareholder = new RegisteredShareholderRequest("Jane Doe", "15-04-1980", "Nigerian", "director",
                "national_id", "doc-id", "2 Home Street", false, "ikeja", "doc-address");
        when(client.saveOwners(eq(context), any()))
                .thenReturn(ComplianceData.newBuilder().setMerchantId("m1").setVersion(4).setStatus("draft").build());

        var result = service.saveRegisteredBusinessShareholders(context,
                new SaveRegisteredShareholdersRequest(java.util.List.of(shareholder)));

        assertEquals(4, result.version());
        verify(client).saveOwners(eq(context),
                argThat(owners -> owners.size() == 1 && "Jane Doe".equals(owners.getFirst().fullName())
                        && "1980-04-15".equals(owners.getFirst().dateOfBirth())
                        && "doc-id".equals(owners.getFirst().idDocumentId())));
    }
}
