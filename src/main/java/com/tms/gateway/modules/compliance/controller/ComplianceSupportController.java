package com.tms.gateway.modules.compliance.controller;
import com.tms.gateway.core.dto.ApiResponse;
import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.modules.compliance.dto.CreateDocumentUploadRequest;
import com.tms.gateway.modules.compliance.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/compliance")
@RequiredArgsConstructor
public class ComplianceSupportController {
    private final ComplianceService compliance;
    private final ComplianceSupportService support;
    @ModelAttribute
    public void preventCaching(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }
    @GetMapping("/reference-data/{kind}")
    public ApiResponse<?> reference(@PathVariable String kind, @RequestParam(defaultValue = "") String parent,
            @RequestParam(defaultValue = "") String country, @RequestParam(defaultValue = "") String currency) {
        return ApiResponse.success(support.referenceData(kind, parent, country, currency));
    }
    @PostMapping("/documents/uploads")
    public ApiResponse<?> upload(@AuthenticationPrincipal GatewayMerchantUserDetails user,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody CreateDocumentUploadRequest request) {
        return ApiResponse.success(support.upload(compliance.mutation(user, key, 0), request));
    }
    @PostMapping("/documents/{id}/complete")
    public ApiResponse<?> complete(@AuthenticationPrincipal GatewayMerchantUserDetails user,
            @RequestHeader("Idempotency-Key") String key, @PathVariable String id) {
        return ApiResponse.success(support.document(compliance.mutation(user, key, 0), id, "complete"));
    }
    @GetMapping("/documents/{id}")
    public ApiResponse<?> get(@AuthenticationPrincipal GatewayMerchantUserDetails user, @PathVariable String id) {
        return ApiResponse.success(support.document(compliance.context(user, null, null), id, "get"));
    }
    @DeleteMapping("/documents/{id}")
    public ApiResponse<?> delete(@AuthenticationPrincipal GatewayMerchantUserDetails user,
            @RequestHeader("Idempotency-Key") String key, @PathVariable String id) {
        return ApiResponse.success(support.document(compliance.mutation(user, key, 0), id, "delete"));
    }
}
