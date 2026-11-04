package com.tms.gateway.modules.customer.controller;

import com.tms.gateway.core.dto.ApiResponse;
import com.tms.gateway.core.exception.AppException;
import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.modules.customer.dto.CreateCustomerRequest;
import com.tms.gateway.modules.customer.dto.CustomerDetailsResponse;
import com.tms.gateway.modules.customer.dto.CustomerListResponse;
import com.tms.gateway.modules.customer.dto.CustomerResponse;
import com.tms.gateway.modules.customer.dto.CustomerTransactionPageResponse;
import com.tms.gateway.modules.customer.dto.UpdateCustomerBlacklistRequest;
import com.tms.gateway.modules.customer.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    public ApiResponse<CustomerListResponse> list(@AuthenticationPrincipal GatewayMerchantUserDetails user,
            @RequestParam(required = false) String search, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.list(merchantId(user), search, page, size), "Customers retrieved");
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
    public ApiResponse<CustomerTransactionPageResponse> transactions(
            @AuthenticationPrincipal GatewayMerchantUserDetails user, @PathVariable("customerRef") String customerRef,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.transactions(merchantId(user), customerRef, page, size),
                "Customer transactions retrieved");
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
