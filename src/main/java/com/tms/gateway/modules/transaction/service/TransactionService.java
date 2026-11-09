package com.tms.gateway.modules.transaction.service;

import com.tms.gateway.core.dto.PagedResponse;
import com.tms.gateway.modules.transaction.dto.TransactionDetailDto;
import com.tms.gateway.modules.transaction.dto.TransactionFiltersDto;
import com.tms.gateway.modules.transaction.dto.TransactionFiltersDto.FilterOption;
import com.tms.gateway.modules.transaction.dto.TransactionListItemDto;
import com.tms.gateway.modules.transaction.dto.TransactionSummaryDto;
import com.tms.gateway.modules.transaction.entity.PgMerchantTransactionEntity;
import com.tms.gateway.modules.transaction.repository.PgMerchantTransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for transaction operations on the merchant dashboard.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final PgMerchantTransactionRepository transactionRepository;

    private static final String BASE_PATH = "/transactions";

    // Status code mappings
    private static final Map<String, String> STATUS_DISPLAY_MAP = Map.of("pending", "Pending", "completed",
            "Successful", "successful", "Successful", "failed", "Failed", "reversed", "Reversed");

    // Payment method mappings
    private static final Map<String, String> PAYMENT_METHOD_DISPLAY_MAP = Map.of("card", "Card", "bank_transfer",
            "Bank Transfer", "wallet", "IRPay Wallet", "ussd", "USSD", "qr", "QR Code");

    // Static filter options for dropdowns
    private static final List<FilterOption> STATUS_OPTIONS = List.of(new FilterOption("completed", "Successful"),
            new FilterOption("failed", "Failed"), new FilterOption("pending", "Pending"),
            new FilterOption("reversed", "Reversed"));

    private static final List<FilterOption> PAYMENT_METHOD_OPTIONS = List.of(new FilterOption("card", "Card"),
            new FilterOption("bank_transfer", "Bank Transfer"), new FilterOption("wallet", "IRPay Wallet"),
            new FilterOption("ussd", "USSD"), new FilterOption("qr", "QR Code"));

    private static final List<Integer> LIMIT_OPTIONS = List.of(10, 20, 50, 100);

    private static final TransactionFiltersDto FILTERS = new TransactionFiltersDto(STATUS_OPTIONS,
            PAYMENT_METHOD_OPTIONS, LIMIT_OPTIONS);

    /**
     * Get transaction summary statistics for dashboard cards.
     */
    @Transactional(readOnly = true)
    public TransactionSummaryDto getSummary(Long userId) {
        List<Object[]> statusSummary = transactionRepository.getSummaryByStatus(userId);

        long totalCount = 0;
        BigDecimal totalValue = BigDecimal.ZERO;
        long successfulCount = 0;
        BigDecimal successfulValue = BigDecimal.ZERO;
        long failedCount = 0;
        BigDecimal failedValue = BigDecimal.ZERO;
        long pendingCount = 0;
        BigDecimal pendingValue = BigDecimal.ZERO;

        for (Object[] row : statusSummary) {
            String status = (String) row[0];
            long count = ((Number) row[1]).longValue();
            BigDecimal value = (BigDecimal) row[2];

            totalCount += count;
            totalValue = totalValue.add(value);

            switch (status.toLowerCase()) {
                case "completed", "successful" -> {
                    successfulCount += count;
                    successfulValue = successfulValue.add(value);
                }
                case "failed" -> {
                    failedCount += count;
                    failedValue = failedValue.add(value);
                }
                case "pending" -> {
                    pendingCount += count;
                    pendingValue = pendingValue.add(value);
                }
            }
        }

        return new TransactionSummaryDto(totalCount, totalValue, successfulCount, successfulValue, failedCount,
                failedValue, pendingCount, pendingValue);
    }

    /**
     * Get paginated list of transactions with optional filters.
     * 
     * @param page
     *            1-indexed page number (first page is 1)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> list(Long userId, String status, String paymentMethod, Instant startDate,
            Instant endDate, String search, int page, int size) {
        // Normalize filters
        String normalizedStatus = normalizeStatus(status);
        String normalizedPaymentMethod = normalizePaymentMethod(paymentMethod);

        // Convert 1-indexed to 0-indexed for Spring Data
        int zeroIndexedPage = Math.max(0, page - 1);

        log.debug(
                "Transaction list request: userId={}, status={} -> {}, paymentMethod={} -> {}, "
                        + "startDate={}, endDate={}, search={}, page={} (0-indexed: {}), size={}",
                userId, status, normalizedStatus, paymentMethod, normalizedPaymentMethod, startDate, endDate, search,
                page, zeroIndexedPage, size);

        Pageable pageable = PageRequest.of(zeroIndexedPage, size);
        Page<PgMerchantTransactionEntity> result = transactionRepository.findByFilters(userId, normalizedStatus,
                normalizedPaymentMethod, startDate, endDate, search, pageable);

        log.debug("Transaction list result: totalElements={}, totalPages={}, contentSize={}", result.getTotalElements(),
                result.getTotalPages(), result.getContent().size());

        // Map entities to DTOs
        List<TransactionListItemDto> data = result.getContent().stream().map(this::toListItem).toList();

        // Create a Page of DTOs for PagedResponse
        Page<TransactionListItemDto> dtoPage = PageableExecutionUtils.getPage(data, pageable, result::getTotalElements);

        // Use PagedResponse utility with filters as extra data
        return PagedResponse.from(dtoPage, BASE_PATH, Map.of("filters", FILTERS));
    }

    /**
     * Get single transaction detail.
     */
    @Transactional(readOnly = true)
    public TransactionDetailDto getDetail(Long userId, String reference) {
        PgMerchantTransactionEntity entity = transactionRepository.findByUserIdAndReference(userId, reference)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + reference));
        return toDetail(entity);
    }

    // ── Mapping helpers ──────────────────────────────────────────────────────────

    private TransactionListItemDto toListItem(PgMerchantTransactionEntity entity) {
        return new TransactionListItemDto(entity.getReference(), entity.getAmount(), entity.getServiceFee(),
                entity.getPaymentMethod(), getPaymentMethodDisplay(entity.getPaymentMethod()),
                entity.getCustomerEmail(), entity.getCustomerName(), entity.getStatusCode(),
                getStatusDisplay(entity.getStatusCode()), entity.getCreatedAt());
    }

    private TransactionDetailDto toDetail(PgMerchantTransactionEntity entity) {
        return new TransactionDetailDto(entity.getReference(), entity.getAmount(), entity.getServiceFee(),
                entity.getProductCode(), entity.getChannel(), entity.getPaymentMethod(),
                getPaymentMethodDisplay(entity.getPaymentMethod()), entity.getCustomerEmail(), entity.getCustomerName(),
                entity.getBankName(), entity.getAccountNumber(), entity.getStatusCode(),
                getStatusDisplay(entity.getStatusCode()), entity.getStatusMessage(), entity.getProviderReference(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private String getStatusDisplay(String statusCode) {
        if (statusCode == null)
            return null;
        return STATUS_DISPLAY_MAP.getOrDefault(statusCode.toLowerCase(), statusCode);
    }

    private String getPaymentMethodDisplay(String paymentMethod) {
        if (paymentMethod == null)
            return null;
        return PAYMENT_METHOD_DISPLAY_MAP.getOrDefault(paymentMethod.toLowerCase(), paymentMethod);
    }

    /**
     * Normalize status filter input to match stored values.
     */
    private String normalizeStatus(String status) {
        if (status == null || status.isBlank())
            return null;
        return switch (status.toLowerCase()) {
            case "successful", "success", "completed" -> "completed";
            case "failed", "failure" -> "failed";
            case "pending" -> "pending";
            case "reversed" -> "reversed";
            default -> status.toLowerCase();
        };
    }

    /**
     * Normalize payment method filter input.
     */
    private String normalizePaymentMethod(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.isBlank())
            return null;
        return switch (paymentMethod.toLowerCase().replace(" ", "_")) {
            case "card" -> "card";
            case "bank_transfer", "banktransfer", "bank" -> "bank_transfer";
            case "wallet", "irpay_wallet", "irpaywallet" -> "wallet";
            case "ussd" -> "ussd";
            case "qr", "qr_code", "qrcode" -> "qr";
            default -> paymentMethod.toLowerCase();
        };
    }
}
