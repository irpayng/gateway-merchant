package com.tms.gateway.modules.compliance.controller;

import com.tms.gateway.core.dto.ApiResponse;
import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.modules.compliance.dto.ComplianceResponse;
import com.tms.gateway.modules.compliance.dto.SaveAgreementRequest;
import com.tms.gateway.modules.compliance.dto.SaveBankAccountsRequest;
import com.tms.gateway.modules.compliance.dto.SaveContactRequest;
import com.tms.gateway.modules.compliance.dto.SaveOwnersRequest;
import com.tms.gateway.modules.compliance.dto.SaveProfileRequest;
import com.tms.gateway.modules.compliance.service.ComplianceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for PG merchant compliance operations.
 *
 * <p>
 * Endpoints:
 * <ul>
 * <li>POST /compliance/start - Start a new compliance application</li>
 * <li>GET /compliance - Get current compliance</li>
 * <li>PUT /compliance/profile - Save step 1</li>
 * <li>PUT /compliance/contact - Save step 2</li>
 * <li>PUT /compliance/owners - Save step 3</li>
 * <li>PUT /compliance/bank-accounts - Save step 4</li>
 * <li>PUT /compliance/agreement - Save step 5</li>
 * <li>POST /compliance/submit - Submit for review</li>
 * </ul>
 */
@RestController
@RequestMapping("/compliance")
public class ComplianceController {

    private final ComplianceService complianceService;

    public ComplianceController(ComplianceService complianceService) {
        this.complianceService = complianceService;
    }

    /**
     * Start a new compliance application (idempotent).
     */
    @PostMapping("/start")
    public ResponseEntity<ApiResponse<ComplianceResponse>> startCompliance(
            @AuthenticationPrincipal GatewayMerchantUserDetails user) {

        ComplianceResponse compliance = complianceService.startCompliance(user.getUserId(), user.getMerchantId());

        return ResponseEntity.ok(ApiResponse.success(compliance, "Compliance started"));
    }

    /**
     * Get current compliance status and data.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<ComplianceResponse>> getCompliance(
            @AuthenticationPrincipal GatewayMerchantUserDetails user) {

        ComplianceResponse compliance = complianceService.getCompliance(user.getUserId());

        if (compliance == null) {
            return ResponseEntity.ok(ApiResponse.success(null, "No compliance application found"));
        }

        return ResponseEntity.ok(ApiResponse.success(compliance, "Compliance retrieved"));
    }

    /**
     * Save step 1: Profile details.
     */
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<ComplianceResponse>> saveProfile(
            @AuthenticationPrincipal GatewayMerchantUserDetails user, @Valid @RequestBody SaveProfileRequest request) {

        ComplianceResponse compliance = complianceService.saveProfile(user.getUserId(), request);

        return ResponseEntity.ok(ApiResponse.success(compliance, "Profile saved"));
    }

    /**
     * Save step 2: Contact information.
     */
    @PutMapping("/contact")
    public ResponseEntity<ApiResponse<ComplianceResponse>> saveContact(
            @AuthenticationPrincipal GatewayMerchantUserDetails user, @Valid @RequestBody SaveContactRequest request) {

        ComplianceResponse compliance = complianceService.saveContact(user.getUserId(), request);

        return ResponseEntity.ok(ApiResponse.success(compliance, "Contact saved"));
    }

    /**
     * Save step 3: Owners/directors.
     */
    @PutMapping("/owners")
    public ResponseEntity<ApiResponse<ComplianceResponse>> saveOwners(
            @AuthenticationPrincipal GatewayMerchantUserDetails user, @Valid @RequestBody SaveOwnersRequest request) {

        ComplianceResponse compliance = complianceService.saveOwners(user.getUserId(), request.owners());

        return ResponseEntity.ok(ApiResponse.success(compliance, "Owners saved"));
    }

    /**
     * Save step 4: Bank accounts.
     */
    @PutMapping("/bank-accounts")
    public ResponseEntity<ApiResponse<ComplianceResponse>> saveBankAccounts(
            @AuthenticationPrincipal GatewayMerchantUserDetails user,
            @Valid @RequestBody SaveBankAccountsRequest request) {

        ComplianceResponse compliance = complianceService.saveBankAccounts(user.getUserId(), request.bankAccounts());

        return ResponseEntity.ok(ApiResponse.success(compliance, "Bank accounts saved"));
    }

    /**
     * Save step 5: Service agreement.
     */
    @PutMapping("/agreement")
    public ResponseEntity<ApiResponse<ComplianceResponse>> saveAgreement(
            @AuthenticationPrincipal GatewayMerchantUserDetails user, @Valid @RequestBody SaveAgreementRequest request,
            HttpServletRequest httpRequest) {

        // Capture IP and user-agent if not provided
        String ipAddress = request.ipAddress();
        String userAgent = request.userAgent();

        if (ipAddress == null || ipAddress.isBlank()) {
            ipAddress = getClientIp(httpRequest);
        }
        if (userAgent == null || userAgent.isBlank()) {
            userAgent = httpRequest.getHeader("User-Agent");
        }

        SaveAgreementRequest enrichedRequest = new SaveAgreementRequest(request.termsAccepted(), request.termsVersion(),
                request.privacyAccepted(), ipAddress, userAgent);

        ComplianceResponse compliance = complianceService.saveAgreement(user.getUserId(), enrichedRequest);

        return ResponseEntity.ok(ApiResponse.success(compliance, "Agreement saved"));
    }

    /**
     * Submit compliance for review.
     */
    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<ComplianceResponse>> submitCompliance(
            @AuthenticationPrincipal GatewayMerchantUserDetails user) {

        ComplianceResponse compliance = complianceService.submitCompliance(user.getUserId());

        return ResponseEntity.ok(ApiResponse.success(compliance, "Compliance submitted for review"));
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}
