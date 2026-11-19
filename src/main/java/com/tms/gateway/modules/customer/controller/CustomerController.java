package com.tms.gateway.modules.customer.controller;

import com.tms.gateway.core.dto.ApiResponse;
import com.tms.gateway.core.exception.AppException;
import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.modules.customer.dto.CreateCustomerRequest;
import com.tms.gateway.modules.customer.dto.CustomerDetailsResponse;
import com.tms.gateway.modules.customer.dto.CustomerResponse;
import com.tms.gateway.modules.customer.dto.UpdateCustomerBlacklistRequest;
import com.tms.gateway.modules.customer.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Merchant customer management")
public class CustomerController {
    private final CustomerService service;

    @GetMapping
    @Operation(summary = "Search customers")
    public ResponseEntity<Map<String, Object>> list(@AuthenticationPrincipal GatewayMerchantUserDetails user,
            @RequestParam(required = false) String search, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.list(merchantId(user), search, page, size));
    }

    @PostMapping
    @Operation(summary = "Add a customer")
    public ApiResponse<CustomerResponse> create(@AuthenticationPrincipal GatewayMerchantUserDetails user,
            @Valid @RequestBody CreateCustomerRequest request) {
        return ApiResponse.success(service.create(merchantId(user), request), "Customer created");
    }

    @GetMapping("/{customerRef}")
    @Operation(summary = "Get customer details and transaction summary")
    public ApiResponse<CustomerDetailsResponse> get(@AuthenticationPrincipal GatewayMerchantUserDetails user,
            @PathVariable("customerRef") String customerRef) {
        return ApiResponse.success(service.get(merchantId(user), customerRef), "Customer retrieved");
    }

    @GetMapping("/{customerRef}/transactions")
    @Operation(summary = "List a customer's transactions")
    public ResponseEntity<Map<String, Object>> transactions(@AuthenticationPrincipal GatewayMerchantUserDetails user,
            @PathVariable("customerRef") String customerRef, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.transactions(merchantId(user), customerRef, page, size));
    }

    @PatchMapping("/{customerRef}/blacklist")
    @Operation(summary = "Set a customer's blacklist display flag")
    public ApiResponse<CustomerResponse> setBlacklisted(@AuthenticationPrincipal GatewayMerchantUserDetails user,
            @PathVariable("customerRef") String customerRef,
            @Valid @RequestBody UpdateCustomerBlacklistRequest request) {
        return ApiResponse.success(
                service.setBlacklisted(merchantId(user), customerRef, Objects.requireNonNull(request.blacklisted())),
                "Customer blacklist status updated");
    }

    private String merchantId(GatewayMerchantUserDetails user) {
        if (user == null || user.getMerchantId() == null || user.getMerchantId().isBlank())
            throw new AppException("Merchant ID is not assigned. Please contact support.", HttpStatus.FORBIDDEN);
        return user.getMerchantId();
    }
}
