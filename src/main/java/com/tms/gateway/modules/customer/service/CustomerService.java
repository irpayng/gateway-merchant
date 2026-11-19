package com.tms.gateway.modules.customer.service;

import com.tms.gateway.core.dto.PagedResponse;
import com.tms.gateway.core.exception.AppException;
import com.tms.gateway.modules.customer.dto.*;
import com.tms.gateway.modules.customer.entity.PgMerchantCustomerEntity;
import com.tms.gateway.modules.customer.repository.PgMerchantCustomerRepository;
import com.tms.gateway.modules.transaction.entity.PgMerchantTransactionEntity;
import com.tms.gateway.modules.transaction.repository.PgMerchantTransactionRepository;
import com.tms.gateway.modules.transaction.repository.PgMerchantTransactionRepository.CustomerTransactionAggregate;
import com.tms.gateway.modules.user.entity.PgMerchantUserEntity;
import com.tms.gateway.modules.user.repository.PgMerchantUserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Map<String, String> STATUS_DISPLAY = Map.of("pending", "Pending", "completed", "Successful",
            "successful", "Successful", "failed", "Failed", "reversed", "Reversed");
    private static final Map<String, String> PAYMENT_METHOD_DISPLAY = Map.of("card", "Card", "bank_transfer",
            "Bank Transfer", "wallet", "IRPay Wallet", "ussd", "USSD", "qr", "QR Code");

    private final PgMerchantCustomerRepository customerRepository;
    private final PgMerchantTransactionRepository transactionRepository;
    private final PgMerchantUserRepository userRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> list(String merchantId, String search, int page, int size) {
        validatePage(page, size);
        int zeroIndexedPage = page - 1; // Convert 1-indexed to 0-indexed

        Map<String, CustomerTransactionAggregate> aggregates = aggregates(merchantId);
        Map<String, PgMerchantCustomerEntity> saved = customerRepository.findAllByMerchantId(merchantId).stream()
                .collect(Collectors.toMap(PgMerchantCustomerEntity::getNormalizedEmail, c -> c));

        Set<String> emails = new HashSet<>(aggregates.keySet());
        emails.addAll(saved.keySet());
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<CustomerResponse> customers = emails.stream()
                .map(email -> customer(merchantId, email, aggregates.get(email), saved.get(email)))
                .filter(c -> matches(c, query))
                .sorted(Comparator
                        .comparing(CustomerResponse::dateAdded, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(CustomerResponse::customerRef))
                .toList();

        long startLong = (long) zeroIndexedPage * size;
        int start = (int) Math.min(startLong, customers.size());
        int end = Math.min(start + size, customers.size());
        List<CustomerResponse> content = customers.subList(start, end);

        return PagedResponse.from(content, customers.size(), page, size, "/customers");
    }

    @Transactional
    public CustomerResponse create(String merchantId, CreateCustomerRequest request) {
        String email = normalizeEmail(request.email());
        Instant now = Instant.now();
        PgMerchantCustomerEntity customer = customerRepository.findByMerchantIdAndNormalizedEmail(merchantId, email)
                .orElseGet(() -> PgMerchantCustomerEntity.builder().customerRef(customerRef(merchantId, email))
                        .merchantId(merchantId).normalizedEmail(email).blacklisted(false).createdAt(now).build());
        if (customer.getCustomerId() != null)
            throw new AppException("A customer with this email already exists", HttpStatus.CONFLICT);

        customer.setCreatedAt(now);
        customer.setCustomerId(UUID.randomUUID().toString());
        customer.setFirstName(request.firstName().trim());
        customer.setLastName(request.lastName().trim());
        customer.setPhoneNumber(request.phoneNumber());
        customer.setUpdatedAt(now);
        try {
            customerRepository.saveAndFlush(customer);
        } catch (DataIntegrityViolationException e) {
            throw new AppException("A customer with this email already exists", HttpStatus.CONFLICT);
        }
        CustomerTransactionAggregate aggregate = aggregates(merchantId).get(email);
        return customer(merchantId, email, aggregate, customer);
    }

    @Transactional(readOnly = true)
    public CustomerDetailsResponse get(String merchantId, String customerRef) {
        ResolvedCustomer resolved = resolve(merchantId, customerRef);
        CustomerResponse customer = customer(merchantId, resolved.email(), resolved.aggregate(), resolved.saved());
        CustomerTransactionAggregate aggregate = resolved.aggregate();
        return new CustomerDetailsResponse(customer.customerRef(), customer.customerId(), customer.email(),
                customer.fullName(), customer.phoneNumber(), customer.dateAdded(), customer.blacklisted(),
                aggregate == null ? java.math.BigDecimal.ZERO : aggregate.getSuccessfulSpend(), "NGN",
                aggregate == null ? 0 : aggregate.getSuccessfulCount(),
                aggregate == null ? 0 : aggregate.getTotalCount());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> transactions(String merchantId, String customerRef, int page, int size) {
        validatePage(page, size);
        int zeroIndexedPage = page - 1; // Convert 1-indexed to 0-indexed

        ResolvedCustomer resolved = resolve(merchantId, customerRef);
        List<Long> userIds = merchantUserIds(merchantId);
        Page<PgMerchantTransactionEntity> result = userIds.isEmpty()
                ? new PageImpl<>(List.of(), PageRequest.of(zeroIndexedPage, size), 0)
                : transactionRepository.findCustomerTransactions(userIds, resolved.email(),
                        PageRequest.of(zeroIndexedPage, size));
        List<CustomerTransactionResponse> content = result.getContent().stream().map(t -> {
            String method = normalized(t.getPaymentMethod());
            String status = normalized(t.getStatusCode());
            return new CustomerTransactionResponse(t.getReference(), t.getAmount(), "NGN", t.getPaymentMethod(),
                    display(PAYMENT_METHOD_DISPLAY, method), t.getStatusCode(), display(STATUS_DISPLAY, status),
                    t.getCreatedAt());
        }).toList();

        return PagedResponse.from(content, result.getTotalElements(), page, size,
                "/customers/" + customerRef + "/transactions");
    }

    @Transactional
    public CustomerResponse setBlacklisted(String merchantId, String customerRef, boolean blacklisted) {
        ResolvedCustomer resolved = resolve(merchantId, customerRef);
        Instant now = Instant.now();
        PgMerchantCustomerEntity customer = resolved.saved();
        if (customer == null) {
            customer = PgMerchantCustomerEntity.builder().customerRef(customerRef).merchantId(merchantId)
                    .normalizedEmail(resolved.email()).createdAt(now).build();
        }
        customer.setBlacklisted(blacklisted);
        customer.setUpdatedAt(now);
        try {
            customerRepository.saveAndFlush(customer);
        } catch (DataIntegrityViolationException e) {
            throw new AppException("Customer state changed concurrently; refresh and retry", HttpStatus.CONFLICT);
        }
        return customer(merchantId, resolved.email(), resolved.aggregate(), customer);
    }

    private ResolvedCustomer resolve(String merchantId, String ref) {
        PgMerchantCustomerEntity saved = customerRepository.findByMerchantIdAndCustomerRef(merchantId, ref)
                .orElse(null);
        if (saved != null)
            return new ResolvedCustomer(saved.getNormalizedEmail(),
                    aggregates(merchantId).get(saved.getNormalizedEmail()), saved);

        for (var entry : aggregates(merchantId).entrySet()) {
            if (customerRef(merchantId, entry.getKey()).equals(ref))
                return new ResolvedCustomer(entry.getKey(), entry.getValue(), null);
        }
        throw new AppException("Customer not found", HttpStatus.NOT_FOUND);
    }

    private Map<String, CustomerTransactionAggregate> aggregates(String merchantId) {
        List<Long> userIds = merchantUserIds(merchantId);
        if (userIds.isEmpty())
            return Map.of();
        return transactionRepository.aggregateCustomers(userIds).stream()
                .collect(Collectors.toMap(CustomerTransactionAggregate::getNormalizedEmail, a -> a));
    }

    private List<Long> merchantUserIds(String merchantId) {
        return userRepository.findAllByMerchantId(merchantId).stream().map(PgMerchantUserEntity::getId).toList();
    }

    private CustomerResponse customer(String merchantId, String email, CustomerTransactionAggregate aggregate,
            PgMerchantCustomerEntity saved) {
        String fullName = saved != null && saved.getCustomerId() != null
                ? joinName(saved.getFirstName(), saved.getLastName())
                : aggregate == null ? null : clean(aggregate.getTransactionName());
        Instant dateAdded = aggregate != null
                ? aggregate.getEarliestTransactionAt()
                : saved == null ? null : saved.getCreatedAt();
        return new CustomerResponse(customerRef(merchantId, email), saved == null ? null : saved.getCustomerId(), email,
                fullName, saved == null ? null : saved.getPhoneNumber(), dateAdded,
                saved != null && saved.isBlacklisted());
    }

    private boolean matches(CustomerResponse customer, String search) {
        if (search.isEmpty())
            return true;
        return contains(customer.email(), search) || contains(customer.fullName(), search)
                || contains(customer.phoneNumber(), search);
    }

    private boolean contains(String value, String search) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(search);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String customerRef(String merchantId, String email) {
        return UUID.nameUUIDFromBytes((merchantId + "\u0000" + email).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String joinName(String first, String last) {
        String full = ((first == null ? "" : first.trim()) + " " + (last == null ? "" : last.trim())).trim();
        return full.isEmpty() ? null : full;
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalized(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private String display(Map<String, String> values, String key) {
        return key == null ? null : values.getOrDefault(key, key);
    }

    private void validatePage(int page, int size) {
        if (page < 1)
            throw new AppException("page must be 1 or greater", HttpStatus.BAD_REQUEST);
        if (size < 1 || size > MAX_PAGE_SIZE)
            throw new AppException("size must be between 1 and " + MAX_PAGE_SIZE, HttpStatus.BAD_REQUEST);
    }

    private record ResolvedCustomer(String email, CustomerTransactionAggregate aggregate,
            PgMerchantCustomerEntity saved) {
    }
}
