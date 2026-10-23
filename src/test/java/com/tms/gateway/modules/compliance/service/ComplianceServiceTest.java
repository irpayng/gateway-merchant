package com.tms.gateway.modules.compliance.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.tms.gateway.modules.compliance.dto.ComplianceResponse;
import com.tms.gateway.modules.compliance.dto.SaveContactRequest;
import com.tms.gateway.modules.compliance.dto.SaveProfileRequest;
import com.tms.gateway.modules.compliance.entity.PgMerchantComplianceReplicaEntity;
import com.tms.gateway.modules.compliance.repository.PgMerchantAgreementReplicaRepository;
import com.tms.gateway.modules.compliance.repository.PgMerchantBankAccountReplicaRepository;
import com.tms.gateway.modules.compliance.repository.PgMerchantComplianceReplicaRepository;
import com.tms.gateway.modules.compliance.repository.PgMerchantContactReplicaRepository;
import com.tms.gateway.modules.compliance.repository.PgMerchantOwnerReplicaRepository;
import com.tms.gateway.modules.compliance.repository.PgMerchantProfileReplicaRepository;
import com.tms.gateway.modules.grpc.compliance.ComplianceData;
import com.tms.gateway.modules.grpc.service.ComplianceGrpcClient;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ComplianceServiceTest {

    @Mock
    private ComplianceGrpcClient grpcClient;

    @Mock
    private PgMerchantComplianceReplicaRepository complianceRepo;

    @Mock
    private PgMerchantProfileReplicaRepository profileRepo;

    @Mock
    private PgMerchantContactReplicaRepository contactRepo;

    @Mock
    private PgMerchantOwnerReplicaRepository ownerRepo;

    @Mock
    private PgMerchantBankAccountReplicaRepository bankAccountRepo;

    @Mock
    private PgMerchantAgreementReplicaRepository agreementRepo;

    private ComplianceService complianceService;

    @BeforeEach
    void setUp() {
        complianceService = new ComplianceService(grpcClient, complianceRepo, profileRepo, contactRepo, ownerRepo,
                bankAccountRepo, agreementRepo);
    }

    @Test
    @DisplayName("startCompliance should call gRPC and return compliance response")
    void startCompliance_shouldCallGrpcAndReturnResponse() {
        // Given
        long userId = 123L;
        String merchantId = "MERCH_001";

        ComplianceData grpcResponse = ComplianceData.newBuilder().setComplianceId(1L).setUserId(userId)
                .setMerchantId(merchantId).setStatus("draft").setCurrentStep(0).build();

        when(grpcClient.startCompliance(userId, merchantId)).thenReturn(grpcResponse);

        // When
        ComplianceResponse response = complianceService.startCompliance(userId, merchantId);

        // Then
        assertNotNull(response);
        assertEquals(1L, response.complianceId());
        assertEquals(userId, response.userId());
        assertEquals(merchantId, response.merchantId());
        assertEquals("draft", response.status());
        assertEquals(0, response.currentStep());

        verify(grpcClient).startCompliance(userId, merchantId);
    }

    @Test
    @DisplayName("getCompliance should return from local replica when available")
    void getCompliance_shouldReturnFromLocalReplicaWhenAvailable() {
        // Given
        long userId = 123L;

        PgMerchantComplianceReplicaEntity replica = PgMerchantComplianceReplicaEntity.builder().id(1L).sourceId(100L)
                .userId(userId).merchantId("MERCH_001").status("draft").currentStep(2).profileComplete(true)
                .contactComplete(true).ownersComplete(false).bankAccountsComplete(false).agreementComplete(false)
                .createdAt(Instant.now()).updatedAt(Instant.now()).syncedAt(Instant.now()).build();

        when(complianceRepo.findByUserId(userId)).thenReturn(Optional.of(replica));
        when(profileRepo.findByComplianceId(1L)).thenReturn(Optional.empty());
        when(contactRepo.findByComplianceId(1L)).thenReturn(Optional.empty());
        when(ownerRepo.findByComplianceId(1L)).thenReturn(java.util.Collections.emptyList());
        when(bankAccountRepo.findByComplianceId(1L)).thenReturn(java.util.Collections.emptyList());
        when(agreementRepo.findByComplianceId(1L)).thenReturn(Optional.empty());

        // When
        ComplianceResponse response = complianceService.getCompliance(userId);

        // Then
        assertNotNull(response);
        assertEquals(100L, response.complianceId()); // sourceId
        assertEquals(userId, response.userId());
        assertEquals("draft", response.status());
        assertEquals(2, response.currentStep());
        assertTrue(response.profileComplete());
        assertTrue(response.contactComplete());
        assertFalse(response.ownersComplete());

        // Should NOT call gRPC since replica exists
        verify(grpcClient, never()).getCompliance(anyLong());
    }

    @Test
    @DisplayName("getCompliance should fallback to gRPC when replica not found")
    void getCompliance_shouldFallbackToGrpcWhenReplicaNotFound() {
        // Given
        long userId = 123L;

        when(complianceRepo.findByUserId(userId)).thenReturn(Optional.empty());

        ComplianceData grpcResponse = ComplianceData.newBuilder().setComplianceId(100L).setUserId(userId)
                .setMerchantId("MERCH_001").setStatus("draft").setCurrentStep(1).setProfileComplete(true).build();

        when(grpcClient.getCompliance(userId)).thenReturn(grpcResponse);

        // When
        ComplianceResponse response = complianceService.getCompliance(userId);

        // Then
        assertNotNull(response);
        assertEquals(100L, response.complianceId());
        assertEquals("draft", response.status());

        verify(grpcClient).getCompliance(userId);
    }

    @Test
    @DisplayName("getCompliance should return null when not found in replica or gRPC")
    void getCompliance_shouldReturnNullWhenNotFound() {
        // Given
        long userId = 123L;

        when(complianceRepo.findByUserId(userId)).thenReturn(Optional.empty());
        when(grpcClient.getCompliance(userId)).thenReturn(null);

        // When
        ComplianceResponse response = complianceService.getCompliance(userId);

        // Then
        assertNull(response);
    }

    @Test
    @DisplayName("isComplianceApproved should return true when status is approved")
    void isComplianceApproved_shouldReturnTrueWhenApproved() {
        // Given
        long userId = 123L;

        PgMerchantComplianceReplicaEntity replica = PgMerchantComplianceReplicaEntity.builder().id(1L).sourceId(100L)
                .userId(userId).merchantId("MERCH_001").status("approved").build();

        when(complianceRepo.findByUserId(userId)).thenReturn(Optional.of(replica));

        // When
        boolean result = complianceService.isComplianceApproved(userId);

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("isComplianceApproved should return false when status is not approved")
    void isComplianceApproved_shouldReturnFalseWhenNotApproved() {
        // Given
        long userId = 123L;

        PgMerchantComplianceReplicaEntity replica = PgMerchantComplianceReplicaEntity.builder().id(1L).sourceId(100L)
                .userId(userId).merchantId("MERCH_001").status("draft").build();

        when(complianceRepo.findByUserId(userId)).thenReturn(Optional.of(replica));

        // When
        boolean result = complianceService.isComplianceApproved(userId);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("saveProfile should call gRPC and return updated compliance")
    void saveProfile_shouldCallGrpcAndReturnUpdatedCompliance() {
        // Given
        long userId = 123L;
        SaveProfileRequest request = new SaveProfileRequest("NG", "technology", "software", "TestCo", "100001-500000",
                "llc", "Test Company Ltd", "RC123456", null);

        ComplianceData grpcResponse = ComplianceData.newBuilder().setComplianceId(100L).setUserId(userId)
                .setMerchantId("MERCH_001").setStatus("draft").setCurrentStep(1).setProfileComplete(true).build();

        when(grpcClient.saveProfile(eq(userId), any(SaveProfileRequest.class))).thenReturn(grpcResponse);

        // When
        ComplianceResponse response = complianceService.saveProfile(userId, request);

        // Then
        assertNotNull(response);
        assertEquals(1, response.currentStep());
        assertTrue(response.profileComplete());

        verify(grpcClient).saveProfile(eq(userId), any(SaveProfileRequest.class));
    }

    @Test
    @DisplayName("saveContact should call gRPC and return updated compliance")
    void saveContact_shouldCallGrpcAndReturnUpdatedCompliance() {
        // Given
        long userId = 123L;
        SaveContactRequest request = new SaveContactRequest("test@example.com", "+2341234567890", "123 Test Street",
                "Lagos", "Lagos", "100001", "NG");

        ComplianceData grpcResponse = ComplianceData.newBuilder().setComplianceId(100L).setUserId(userId)
                .setMerchantId("MERCH_001").setStatus("draft").setCurrentStep(2).setProfileComplete(true)
                .setContactComplete(true).build();

        when(grpcClient.saveContact(eq(userId), any(SaveContactRequest.class))).thenReturn(grpcResponse);

        // When
        ComplianceResponse response = complianceService.saveContact(userId, request);

        // Then
        assertNotNull(response);
        assertEquals(2, response.currentStep());
        assertTrue(response.contactComplete());

        verify(grpcClient).saveContact(eq(userId), any(SaveContactRequest.class));
    }

    @Test
    @DisplayName("submitCompliance should call gRPC and return submitted compliance")
    void submitCompliance_shouldCallGrpcAndReturnSubmittedCompliance() {
        // Given
        long userId = 123L;

        ComplianceData grpcResponse = ComplianceData.newBuilder().setComplianceId(100L).setUserId(userId)
                .setMerchantId("MERCH_001").setStatus("submitted").setCurrentStep(5).setProfileComplete(true)
                .setContactComplete(true).setOwnersComplete(true).setBankAccountsComplete(true)
                .setAgreementComplete(true).build();

        when(grpcClient.submitCompliance(userId)).thenReturn(grpcResponse);

        // When
        ComplianceResponse response = complianceService.submitCompliance(userId);

        // Then
        assertNotNull(response);
        assertEquals("submitted", response.status());
        assertTrue(response.profileComplete());
        assertTrue(response.contactComplete());
        assertTrue(response.ownersComplete());
        assertTrue(response.bankAccountsComplete());
        assertTrue(response.agreementComplete());

        verify(grpcClient).submitCompliance(userId);
    }
}
