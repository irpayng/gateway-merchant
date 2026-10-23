package com.tms.gateway.modules.grpc.service;

import com.tms.gateway.core.exception.GrpcException;
import com.tms.gateway.modules.compliance.dto.SaveAgreementRequest;
import com.tms.gateway.modules.compliance.dto.SaveBankAccountRequest;
import com.tms.gateway.modules.compliance.dto.SaveContactRequest;
import com.tms.gateway.modules.compliance.dto.SaveOwnerRequest;
import com.tms.gateway.modules.compliance.dto.SaveProfileRequest;
import com.tms.gateway.modules.grpc.compliance.BankAccountData;
import com.tms.gateway.modules.grpc.compliance.ComplianceData;
import com.tms.gateway.modules.grpc.compliance.ComplianceResponse;
import com.tms.gateway.modules.grpc.compliance.GetComplianceRequest;
import com.tms.gateway.modules.grpc.compliance.OwnerData;
import com.tms.gateway.modules.grpc.compliance.PgMerchantComplianceServiceGrpc;
import com.tms.gateway.modules.grpc.compliance.StartComplianceRequest;
import com.tms.gateway.modules.grpc.compliance.SubmitComplianceRequest;
import com.tms.gateway.modules.grpc.config.GrpcProperties;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * gRPC client for PG merchant compliance operations in tms-kyc.
 */
@Service
public class ComplianceGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(ComplianceGrpcClient.class);
    private static final String SERVICE_NAME = "kyc-service";

    private final GrpcProperties grpcProperties;
    private ManagedChannel channel;
    private PgMerchantComplianceServiceGrpc.PgMerchantComplianceServiceBlockingStub stub;

    public ComplianceGrpcClient(GrpcProperties grpcProperties) {
        this.grpcProperties = grpcProperties;
    }

    @PostConstruct
    public void init() {
        String host = grpcProperties.getKycServiceHost();
        int port = grpcProperties.getKycServicePort();
        log.info("Initializing ComplianceGrpcClient connecting to {}:{}", host, port);

        channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        stub = PgMerchantComplianceServiceGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Start a new compliance application.
     */
    public ComplianceData startCompliance(long userId, String merchantId) {
        try {
            ComplianceResponse response = stub.startCompliance(
                    StartComplianceRequest.newBuilder().setUserId(userId).setMerchantId(merchantId).build());

            if (!response.getSuccess()) {
                throw new GrpcException(SERVICE_NAME, "startCompliance", "FAILED", response.getMessage());
            }
            return response.getCompliance();
        } catch (StatusRuntimeException e) {
            log.error("gRPC error in startCompliance: {}", e.getStatus());
            throw new GrpcException(SERVICE_NAME, "startCompliance", e.getStatus().getCode().name(),
                    "Failed to start compliance: " + e.getStatus().getDescription());
        }
    }

    /**
     * Get current compliance with full step data.
     */
    public ComplianceData getCompliance(long userId) {
        try {
            ComplianceResponse response = stub
                    .getCompliance(GetComplianceRequest.newBuilder().setUserId(userId).build());

            if (!response.getSuccess()) {
                return null; // Compliance not found
            }
            return response.getCompliance();
        } catch (StatusRuntimeException e) {
            log.error("gRPC error in getCompliance: {}", e.getStatus());
            throw new GrpcException(SERVICE_NAME, "getCompliance", e.getStatus().getCode().name(),
                    "Failed to get compliance: " + e.getStatus().getDescription());
        }
    }

    /**
     * Save step 1: Profile details.
     */
    public ComplianceData saveProfile(long userId, SaveProfileRequest request) {
        try {
            ComplianceResponse response = stub.saveProfile(com.tms.gateway.modules.grpc.compliance.SaveProfileRequest
                    .newBuilder().setUserId(userId).setCountry(nullToEmpty(request.country()))
                    .setIndustry(nullToEmpty(request.industry())).setCategory(nullToEmpty(request.category()))
                    .setTradingName(nullToEmpty(request.tradingName()))
                    .setSalesVolume(nullToEmpty(request.salesVolume()))
                    .setBusinessType(nullToEmpty(request.businessType()))
                    .setBusinessName(nullToEmpty(request.businessName())).setRcNumber(nullToEmpty(request.rcNumber()))
                    .setCacUrl(nullToEmpty(request.cacUrl())).build());

            if (!response.getSuccess()) {
                throw new GrpcException(SERVICE_NAME, "saveProfile", "FAILED", response.getMessage());
            }
            return response.getCompliance();
        } catch (StatusRuntimeException e) {
            log.error("gRPC error in saveProfile: {}", e.getStatus());
            throw new GrpcException(SERVICE_NAME, "saveProfile", e.getStatus().getCode().name(),
                    "Failed to save profile: " + e.getStatus().getDescription());
        }
    }

    /**
     * Save step 2: Contact information.
     */
    public ComplianceData saveContact(long userId, SaveContactRequest request) {
        try {
            ComplianceResponse response = stub.saveContact(
                    com.tms.gateway.modules.grpc.compliance.SaveContactRequest.newBuilder().setUserId(userId)
                            .setEmail(nullToEmpty(request.email())).setPhone(nullToEmpty(request.phone()))
                            .setAddress(nullToEmpty(request.address())).setCity(nullToEmpty(request.city()))
                            .setState(nullToEmpty(request.state())).setPostalCode(nullToEmpty(request.postalCode()))
                            .setCountry(nullToEmpty(request.country())).build());

            if (!response.getSuccess()) {
                throw new GrpcException(SERVICE_NAME, "saveContact", "FAILED", response.getMessage());
            }
            return response.getCompliance();
        } catch (StatusRuntimeException e) {
            log.error("gRPC error in saveContact: {}", e.getStatus());
            throw new GrpcException(SERVICE_NAME, "saveContact", e.getStatus().getCode().name(),
                    "Failed to save contact: " + e.getStatus().getDescription());
        }
    }

    /**
     * Save step 3: Owners.
     */
    public ComplianceData saveOwners(long userId, List<SaveOwnerRequest> owners) {
        try {
            var builder = com.tms.gateway.modules.grpc.compliance.SaveOwnersRequest.newBuilder().setUserId(userId);

            for (SaveOwnerRequest o : owners) {
                builder.addOwners(OwnerData.newBuilder().setId(o.id() != null ? o.id() : 0)
                        .setRole(nullToEmpty(o.role())).setFirstName(nullToEmpty(o.firstName()))
                        .setLastName(nullToEmpty(o.lastName())).setEmail(nullToEmpty(o.email()))
                        .setPhone(nullToEmpty(o.phone())).setBvn(nullToEmpty(o.bvn())).setNin(nullToEmpty(o.nin()))
                        .setDateOfBirth(nullToEmpty(o.dateOfBirth())).setAddress(nullToEmpty(o.address()))
                        .setOwnershipPercentage(o.ownershipPercentage() != null ? o.ownershipPercentage() : 0)
                        .setIdDocumentUrl(nullToEmpty(o.idDocumentUrl()))
                        .setIdDocumentType(nullToEmpty(o.idDocumentType())).build());
            }

            ComplianceResponse response = stub.saveOwners(builder.build());

            if (!response.getSuccess()) {
                throw new GrpcException(SERVICE_NAME, "saveOwners", "FAILED", response.getMessage());
            }
            return response.getCompliance();
        } catch (StatusRuntimeException e) {
            log.error("gRPC error in saveOwners: {}", e.getStatus());
            throw new GrpcException(SERVICE_NAME, "saveOwners", e.getStatus().getCode().name(),
                    "Failed to save owners: " + e.getStatus().getDescription());
        }
    }

    /**
     * Save step 4: Bank accounts.
     */
    public ComplianceData saveBankAccounts(long userId, List<SaveBankAccountRequest> accounts) {
        try {
            var builder = com.tms.gateway.modules.grpc.compliance.SaveBankAccountsRequest.newBuilder()
                    .setUserId(userId);

            for (SaveBankAccountRequest a : accounts) {
                builder.addBankAccounts(BankAccountData.newBuilder().setId(a.id() != null ? a.id() : 0)
                        .setBankCode(nullToEmpty(a.bankCode())).setBankName(nullToEmpty(a.bankName()))
                        .setAccountNumber(nullToEmpty(a.accountNumber())).setAccountName(nullToEmpty(a.accountName()))
                        .setIsPrimary(a.isPrimary()).setCurrency(nullToEmpty(a.currency())).build());
            }

            ComplianceResponse response = stub.saveBankAccounts(builder.build());

            if (!response.getSuccess()) {
                throw new GrpcException(SERVICE_NAME, "saveBankAccounts", "FAILED", response.getMessage());
            }
            return response.getCompliance();
        } catch (StatusRuntimeException e) {
            log.error("gRPC error in saveBankAccounts: {}", e.getStatus());
            throw new GrpcException(SERVICE_NAME, "saveBankAccounts", e.getStatus().getCode().name(),
                    "Failed to save bank accounts: " + e.getStatus().getDescription());
        }
    }

    /**
     * Save step 5: Agreement.
     */
    public ComplianceData saveAgreement(long userId, SaveAgreementRequest request) {
        try {
            ComplianceResponse response = stub
                    .saveAgreement(com.tms.gateway.modules.grpc.compliance.SaveAgreementRequest.newBuilder()
                            .setUserId(userId).setTermsAccepted(request.termsAccepted())
                            .setTermsVersion(nullToEmpty(request.termsVersion()))
                            .setPrivacyAccepted(request.privacyAccepted())
                            .setIpAddress(nullToEmpty(request.ipAddress()))
                            .setUserAgent(nullToEmpty(request.userAgent())).build());

            if (!response.getSuccess()) {
                throw new GrpcException(SERVICE_NAME, "saveAgreement", "FAILED", response.getMessage());
            }
            return response.getCompliance();
        } catch (StatusRuntimeException e) {
            log.error("gRPC error in saveAgreement: {}", e.getStatus());
            throw new GrpcException(SERVICE_NAME, "saveAgreement", e.getStatus().getCode().name(),
                    "Failed to save agreement: " + e.getStatus().getDescription());
        }
    }

    /**
     * Submit compliance for review.
     */
    public ComplianceData submitCompliance(long userId) {
        try {
            ComplianceResponse response = stub
                    .submitCompliance(SubmitComplianceRequest.newBuilder().setUserId(userId).build());

            if (!response.getSuccess()) {
                throw new GrpcException(SERVICE_NAME, "submitCompliance", "FAILED", response.getMessage());
            }
            return response.getCompliance();
        } catch (StatusRuntimeException e) {
            log.error("gRPC error in submitCompliance: {}", e.getStatus());
            throw new GrpcException(SERVICE_NAME, "submitCompliance", e.getStatus().getCode().name(),
                    "Failed to submit compliance: " + e.getStatus().getDescription());
        }
    }

    private String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
