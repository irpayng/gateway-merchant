package com.tms.gateway.modules.compliance.controller;
import com.tms.gateway.core.dto.ApiResponse;
import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.modules.compliance.dto.*;
import com.tms.gateway.modules.compliance.service.ComplianceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/compliance")
@RequiredArgsConstructor
public class ComplianceController {
    private final ComplianceService service;
    @PostMapping("/start")
    public ResponseEntity<ApiResponse<ComplianceResponse>> start(
            @AuthenticationPrincipal GatewayMerchantUserDetails user, @RequestHeader("Idempotency-Key") String key) {
        return ok(service.startCompliance(service.mutation(user, key, 0)), "Compliance started");
    }
    @GetMapping
    public ResponseEntity<ApiResponse<ComplianceResponse>> get(
            @AuthenticationPrincipal GatewayMerchantUserDetails user) {
        return ok(service.getCompliance(service.context(user, null, null)), "Compliance retrieved");
    }
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<ComplianceResponse>> saveProfile(
            @AuthenticationPrincipal GatewayMerchantUserDetails user, @RequestHeader("Idempotency-Key") String key,
            @RequestHeader("X-Expected-Version") long version, @Valid @RequestBody SaveProfileRequest request) {
        return ok(service.saveProfile(service.mutation(user, key, version), request), "Draft saved");
    }
    @PutMapping("/contact")
    public ResponseEntity<ApiResponse<ComplianceResponse>> saveContact(
            @AuthenticationPrincipal GatewayMerchantUserDetails user, @RequestHeader("Idempotency-Key") String key,
            @RequestHeader("X-Expected-Version") long version, @Valid @RequestBody SaveContactRequest request) {
        return ok(service.saveContact(service.mutation(user, key, version), request), "Draft saved");
    }
    @PutMapping("/owners")
    public ResponseEntity<ApiResponse<ComplianceResponse>> saveOwners(
            @AuthenticationPrincipal GatewayMerchantUserDetails user, @RequestHeader("Idempotency-Key") String key,
            @RequestHeader("X-Expected-Version") long version, @Valid @RequestBody SaveOwnersRequest request) {
        return ok(service.saveOwners(service.mutation(user, key, version), request), "Draft saved");
    }
    @PutMapping("/bank-accounts")
    public ResponseEntity<ApiResponse<ComplianceResponse>> saveBankAccounts(
            @AuthenticationPrincipal GatewayMerchantUserDetails user, @RequestHeader("Idempotency-Key") String key,
            @RequestHeader("X-Expected-Version") long version, @Valid @RequestBody SaveBankAccountsRequest request) {
        return ok(service.saveBankAccounts(service.mutation(user, key, version), request), "Draft saved");
    }
    @PutMapping("/registration")
    public ResponseEntity<ApiResponse<ComplianceResponse>> saveRegistration(
            @AuthenticationPrincipal GatewayMerchantUserDetails user, @RequestHeader("Idempotency-Key") String key,
            @RequestHeader("X-Expected-Version") long version, @Valid @RequestBody SaveRegistrationRequest request) {
        return ok(service.saveRegistration(service.mutation(user, key, version), request), "Draft saved");
    }
    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<ComplianceResponse>> submit(
            @AuthenticationPrincipal GatewayMerchantUserDetails user, @RequestHeader("Idempotency-Key") String key,
            @RequestHeader("X-Expected-Version") long version, @Valid @RequestBody SubmitComplianceRequest request,
            HttpServletRequest http) {
        return ok(service.submitCompliance(service.mutation(user, key, version), request, http.getRemoteAddr(),
                http.getHeader("User-Agent")), "Compliance submitted for review");
    }
    private ResponseEntity<ApiResponse<ComplianceResponse>> ok(ComplianceResponse data, String message) {
        var response = ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore());
        if (data != null)
            response.eTag(Long.toString(data.version()));
        return response.body(ApiResponse.success(data, message));
    }
}
