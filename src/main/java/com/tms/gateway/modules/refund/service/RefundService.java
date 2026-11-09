package com.tms.gateway.modules.refund.service;

import com.google.protobuf.Timestamp;
import com.tms.gateway.core.dto.PagedResponse;
import com.tms.gateway.core.exception.AppException;
import com.tms.gateway.grpc.transaction.GetRefundRequest;
import com.tms.gateway.grpc.transaction.ListRefundsRequest;
import com.tms.gateway.grpc.transaction.ListRefundsResponse;
import com.tms.gateway.grpc.transaction.Refund;
import com.tms.gateway.grpc.transaction.RefundStatus;
import com.tms.gateway.modules.grpc.service.TransactionGrpcClient;
import com.tms.gateway.modules.refund.dto.RefundDetailDto;
import com.tms.gateway.modules.refund.dto.RefundListItemDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefundService {

    private final TransactionGrpcClient transactionGrpcClient;

    @Transactional(readOnly = true)
    public Map<String, Object> list(Long userId, String search, String status, Instant startDate, Instant endDate,
            int page, int size) {
        validateFilters(status, startDate, endDate, page, size);
        int zeroIndexedPage = page - 1; // Convert 1-indexed to 0-indexed

        ListRefundsRequest.Builder request = ListRefundsRequest.newBuilder().setMerchantUserId(userId)
                .setPage(zeroIndexedPage).setPageSize(size);
        if (search != null) {
            request.setSearch(search);
        }
        if (status != null && !status.isBlank()) {
            request.setStatus(RefundStatus.REFUND_STATUS_PROCESSED);
        }
        if (startDate != null) {
            request.setCreatedAtFrom(toTimestamp(startDate));
        }
        if (endDate != null) {
            request.setCreatedAtTo(toTimestamp(endDate));
        }

        ListRefundsResponse response = transactionGrpcClient.listRefunds(request.build());
        List<RefundListItemDto> content = response.getRefundsList().stream().map(this::toListItem).toList();

        return PagedResponse.from(content, response.getTotalElements(), page, size, "/refunds");
    }

    @Transactional(readOnly = true)
    public RefundDetailDto getDetail(Long userId, String refundId) {
        if (refundId == null || refundId.isBlank()) {
            throw new IllegalArgumentException("refundId must not be blank");
        }
        Refund refund = transactionGrpcClient
                .getRefund(GetRefundRequest.newBuilder().setMerchantUserId(userId).setRefundId(refundId).build());
        return toDetail(refund);
    }

    private void validateFilters(String status, Instant startDate, Instant endDate, int page, int size) {
        if (status != null && !status.isBlank() && !"processed".equalsIgnoreCase(status.trim())) {
            throw new IllegalArgumentException("status must be processed");
        }
        if (startDate != null && endDate != null && !startDate.isBefore(endDate)) {
            throw new IllegalArgumentException("start_date must be earlier than end_date");
        }
        if (page < 1) {
            throw new IllegalArgumentException("page must be 1 or greater");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be greater than zero");
        }
    }

    private RefundListItemDto toListItem(Refund refund) {
        if (!refund.hasCreatedAt()) {
            throw new AppException("Transaction service omitted refund creation time", HttpStatus.BAD_GATEWAY);
        }
        return new RefundListItemDto(refund.getRefundId(), parseAmount(refund.getAmount()), refund.getCurrencyCode(),
                refund.getCustomerName(), refund.getCustomerEmail(), statusCode(refund.getStatus()),
                statusDisplay(refund.getStatus()), fromTimestamp(refund.getCreatedAt()));
    }

    private RefundDetailDto toDetail(Refund refund) {
        if (!refund.hasCreatedAt() || !refund.hasRefundedAt()) {
            throw new AppException("Transaction service omitted a required refund timestamp", HttpStatus.BAD_GATEWAY);
        }
        return new RefundDetailDto(refund.getRefundId(), parseAmount(refund.getAmount()), refund.getCurrencyCode(),
                statusCode(refund.getStatus()), statusDisplay(refund.getStatus()), refund.getTransactionReference(),
                refund.getRefundType(), fromTimestamp(refund.getCreatedAt()), fromTimestamp(refund.getRefundedAt()),
                refund.getRefundInitiatedBy(), refund.getRefundChannel(), refund.getMerchantName(),
                refund.getMerchantEmail(), refund.getMerchantId());
    }

    private BigDecimal parseAmount(String amount) {
        try {
            return new BigDecimal(amount);
        } catch (NumberFormatException ex) {
            throw new AppException("Transaction service returned an invalid refund amount", HttpStatus.BAD_GATEWAY);
        }
    }

    private String statusCode(RefundStatus status) {
        if (status != RefundStatus.REFUND_STATUS_PROCESSED) {
            throw new AppException("Transaction service returned an unsupported refund status", HttpStatus.BAD_GATEWAY);
        }
        return "processed";
    }

    private String statusDisplay(RefundStatus status) {
        statusCode(status);
        return "Processed";
    }

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    }

    private Instant fromTimestamp(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}
