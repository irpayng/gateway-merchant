package com.tms.gateway.modules.compliance.service;
import com.tms.gateway.core.exception.AppException;
import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.modules.compliance.dto.*;
import com.tms.gateway.modules.grpc.compliance.ComplianceData;
import com.tms.gateway.modules.grpc.compliance.MutationContext;
import com.tms.gateway.modules.grpc.service.ComplianceGrpcClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
/**
 * Authoritative reads avoid stale drafts and approval decisions while Kafka
 * catches up.
 */
@Service
@RequiredArgsConstructor
public class ComplianceService {
    private final ComplianceGrpcClient grpcClient;
    private final ComplianceMapper mapper;
    public MutationContext context(GatewayMerchantUserDetails user, String key, Long version) {
        if (user == null) {
            throw new AppException("Authentication required", HttpStatus.UNAUTHORIZED);
        }
        if (!user.isEnabled()) {
            throw new AppException("Account is disabled or not verified", HttpStatus.FORBIDDEN);
        }
        if (user.getUserId() == null) {
            throw new AppException("User ID is missing", HttpStatus.FORBIDDEN);
        }
        if (user.getMerchantId() == null || user.getMerchantId().isBlank()) {
            throw new AppException("Merchant ID is not assigned. Please contact support.", HttpStatus.FORBIDDEN);
        }
        if (key != null && (!key.matches("[A-Za-z0-9._:-]{1,128}")))
            throw new AppException("Invalid Idempotency-Key");
        if (version != null && version < 0)
            throw new AppException("Expected version must be nonnegative");
        return MutationContext.newBuilder().setActorUserId(user.getUserId()).setMerchantId(user.getMerchantId())
                .setIdempotencyKey(key == null ? "" : key).setExpectedVersion(version == null ? 0 : version).build();
    }
    public MutationContext mutation(GatewayMerchantUserDetails user, String key, long version) {
        if (key == null || key.isBlank())
            throw new AppException("Idempotency-Key is required");
        return context(user, key, version);
    }
    private ComplianceResponse checked(MutationContext c, ComplianceData d) {
        if (d == null)
            return null;
        if (!c.getMerchantId().equals(d.getMerchantId()))
            throw new AppException("KYC returned a different merchant", HttpStatus.BAD_GATEWAY);
        return mapper.toResponse(d);
    }
    public ComplianceResponse startCompliance(MutationContext c) {
        return checked(c, grpcClient.startCompliance(c));
    }
    public ComplianceResponse getCompliance(MutationContext c) {
        return checked(c, grpcClient.getCompliance(c));
    }
    public ComplianceResponse saveProfile(MutationContext c, SaveProfileRequest r) {
        ComplianceDraftValidator.profile(r);
        return checked(c, grpcClient.saveProfile(c, r));
    }
    public ComplianceResponse saveContact(MutationContext c, SaveContactRequest r) {
        ComplianceDraftValidator.contact(r);
        return checked(c, grpcClient.saveContact(c, r));
    }
    public ComplianceResponse saveOwners(MutationContext c, SaveOwnersRequest r) {
        ComplianceDraftValidator.owners(r);
        return checked(c, grpcClient.saveOwners(c, r.owners()));
    }
    public ComplianceResponse saveBankAccounts(MutationContext c, SaveBankAccountsRequest r) {
        ComplianceDraftValidator.accounts(r);
        return checked(c, grpcClient.saveBankAccounts(c, r.bankAccounts()));
    }
    public ComplianceResponse saveRegistration(MutationContext c, SaveRegistrationRequest r) {
        return checked(c, grpcClient.saveRegistration(c, r));
    }
    public ComplianceResponse submitCompliance(MutationContext c, SubmitComplianceRequest r, String ip, String agent) {
        if (!Boolean.TRUE.equals(r.agreementAccepted()))
            throw new AppException("Agreement must be accepted", HttpStatus.UNPROCESSABLE_ENTITY);
        // Source of truth validates the complete application, documents and state
        // inside its submission transaction.
        return checked(c, grpcClient.submitCompliance(c, true, ip, agent));
    }
}
