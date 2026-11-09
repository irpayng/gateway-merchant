package com.tms.gateway.modules.refund.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.Timestamp;
import com.tms.gateway.core.exception.AppException;
import com.tms.gateway.grpc.transaction.GetRefundRequest;
import com.tms.gateway.grpc.transaction.ListRefundsRequest;
import com.tms.gateway.grpc.transaction.ListRefundsResponse;
import com.tms.gateway.grpc.transaction.Refund;
import com.tms.gateway.grpc.transaction.RefundStatus;
import com.tms.gateway.modules.grpc.service.TransactionGrpcClient;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RefundServiceTest {

    private final TransactionGrpcClient client = org.mockito.Mockito.mock(TransactionGrpcClient.class);
    private final RefundService service = new RefundService(client);

    @Test
    void listMapsScreenFiltersAndResponse() {
        Instant from = Instant.parse("2025-07-15T00:00:00Z");
        Instant to = Instant.parse("2025-07-16T00:00:00Z");
        Refund refund = Refund.newBuilder().setRefundId("refund-1").setAmount("15000.00").setCurrencyCode("NGN")
                .setCustomerName("John Doe").setCustomerEmail("john@example.com")
                .setStatus(RefundStatus.REFUND_STATUS_PROCESSED)
                .setCreatedAt(Timestamp.newBuilder().setSeconds(from.getEpochSecond())).build();
        when(client.listRefunds(any())).thenReturn(ListRefundsResponse.newBuilder().addRefunds(refund).setPage(0)
                .setPageSize(10).setTotalElements(11).setTotalPages(2).setHasPrevious(true).build());

        var result = service.list(42L, "John", "Processed", from, to, 1, 10);

        ArgumentCaptor<ListRefundsRequest> captor = ArgumentCaptor.forClass(ListRefundsRequest.class);
        verify(client).listRefunds(captor.capture());
        ListRefundsRequest request = captor.getValue();
        assertEquals(42L, request.getMerchantUserId());
        assertEquals("John", request.getSearch());
        assertEquals(RefundStatus.REFUND_STATUS_PROCESSED, request.getStatus());
        assertEquals(from.getEpochSecond(), request.getCreatedAtFrom().getSeconds());
        assertEquals(to.getEpochSecond(), request.getCreatedAtTo().getSeconds());
        assertEquals(0, request.getPage());
        assertEquals(10, request.getPageSize());
        assertEquals(1, result.content().size());
        assertEquals("Processed", result.content().getFirst().statusDisplay());
        assertEquals("15000.00", result.content().getFirst().amount().toPlainString());
        assertEquals(11, result.totalElements());
        assertEquals(1, result.page());
    }

    @Test
    void listAllowsOnlyProcessedStatus() {
        assertThrows(IllegalArgumentException.class, () -> service.list(42L, null, "pending", null, null, 1, 20));
        verify(client, never()).listRefunds(any());
    }

    @Test
    void detailMapsScreenFieldsAndScopesLookup() {
        Instant createdAt = Instant.parse("2025-07-15T08:20:00Z");
        Instant refundedAt = Instant.parse("2025-07-15T09:20:00Z");
        Refund refund = Refund.newBuilder().setRefundId("refund-1").setAmount("15000.00").setCurrencyCode("NGN")
                .setStatus(RefundStatus.REFUND_STATUS_PROCESSED).setTransactionReference("transaction-1")
                .setRefundType("Full").setCreatedAt(toTimestamp(createdAt)).setRefundedAt(toTimestamp(refundedAt))
                .setRefundInitiatedBy("john@example.com").setRefundChannel("Original Payment")
                .setMerchantName("Shoprite Supermarket").setMerchantEmail("shoprite@gmail.com")
                .setMerchantId("merchant-1").build();
        when(client.getRefund(any())).thenReturn(refund);

        var result = service.getDetail(42L, "refund-1");

        ArgumentCaptor<GetRefundRequest> captor = ArgumentCaptor.forClass(GetRefundRequest.class);
        verify(client).getRefund(captor.capture());
        assertEquals(42L, captor.getValue().getMerchantUserId());
        assertEquals("refund-1", captor.getValue().getRefundId());
        assertEquals("transaction-1", result.transactionReference());
        assertEquals("Full", result.refundType());
        assertEquals(createdAt, result.createdAt());
        assertEquals(refundedAt, result.refundedAt());
        assertEquals("Original Payment", result.refundChannel());
        assertEquals("merchant-1", result.merchantId());
    }

    @Test
    void listRejectsInvalidDateRangeAndPagination() {
        Instant instant = Instant.parse("2025-07-15T00:00:00Z");
        assertThrows(IllegalArgumentException.class,
                () -> service.list(42L, null, null, instant, instant, 1, 20));
        assertThrows(IllegalArgumentException.class, () -> service.list(42L, null, null, null, null, 0, 20));
        assertThrows(IllegalArgumentException.class, () -> service.list(42L, null, null, null, null, -1, 20));
        assertThrows(IllegalArgumentException.class, () -> service.list(42L, null, null, null, null, 1, 0));
        verify(client, never()).listRefunds(any());
    }

    @Test
    void invalidUpstreamAmountFailsAsBadGateway() {
        when(client.listRefunds(any())).thenReturn(ListRefundsResponse.newBuilder()
                .addRefunds(Refund.newBuilder().setAmount("invalid").setStatus(RefundStatus.REFUND_STATUS_PROCESSED))
                .build());

        AppException exception = assertThrows(AppException.class,
                () -> service.list(42L, null, null, null, null, 1, 20));

        assertEquals(502, exception.getStatus().value());
    }

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    }
}
