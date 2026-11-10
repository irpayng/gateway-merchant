package com.tms.gateway.modules.refund.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.protobuf.Timestamp;
import com.tms.gateway.core.exception.AppException;
import com.tms.gateway.grpc.transaction.GetRefundRequest;
import com.tms.gateway.grpc.transaction.ListRefundsRequest;
import com.tms.gateway.grpc.transaction.RefundStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MockRefundDataProviderTest {

    private final MockRefundDataProvider provider = new MockRefundDataProvider();

    @Test
    void listsAndPaginatesScreenSampleRefunds() {
        var firstPage = provider.list(ListRefundsRequest.newBuilder().setPage(0).setPageSize(5).build());
        var secondPage = provider.list(ListRefundsRequest.newBuilder().setPage(1).setPageSize(5).build());

        assertEquals(50, firstPage.getTotalElements());
        assertEquals(10, firstPage.getTotalPages());
        assertEquals(5, firstPage.getRefundsCount());
        assertEquals(1, secondPage.getPage());
        assertEquals(5, secondPage.getRefundsCount());
        assertEquals("refund-john-doe", firstPage.getRefunds(0).getRefundId());
        assertEquals("John Doe", firstPage.getRefunds(0).getCustomerName());
        assertEquals("Peter Griffin", firstPage.getRefunds(1).getCustomerName());
        assertEquals("Joseph Adeyemi", firstPage.getRefunds(2).getCustomerName());
        assertEquals("Matthew Paul", firstPage.getRefunds(3).getCustomerName());
        assertEquals("Andrew Emmanuel", firstPage.getRefunds(4).getCustomerName());
    }

    @Test
    void filtersBySearchAndCreationTime() {
        Instant from = Instant.parse("2025-07-15T00:00:00Z");
        Instant to = Instant.parse("2025-07-16T00:00:00Z");
        var response = provider.list(ListRefundsRequest.newBuilder().setSearch("Peter Griffin").setPage(0)
                .setPageSize(20).setCreatedAtFrom(timestamp(from)).setCreatedAtTo(timestamp(to)).build());

        assertEquals(1, response.getTotalElements());
        assertEquals("Peter Griffin", response.getRefunds(0).getCustomerName());
    }

    @Test
    void searchesDisplayedCurrencyAmount() {
        var response = provider
                .list(ListRefundsRequest.newBuilder().setSearch("\u20a615,000.00").setPage(0).setPageSize(5).build());

        assertEquals(50, response.getTotalElements());
        assertEquals(5, response.getRefundsCount());
    }

    @Test
    void retrievesDetailAndReturnsNotFoundForUnknownId() {
        var refund = provider.get(GetRefundRequest.newBuilder().setRefundId("refund-john-doe").build());

        assertEquals("15000.00", refund.getAmount());
        assertEquals(RefundStatus.REFUND_STATUS_PROCESSED, refund.getStatus());
        assertEquals("Full", refund.getRefundType());
        assertEquals("Original Payment", refund.getRefundChannel());
        assertEquals("b5e4b374-5cb1-4138-8dff-fdd2d3de8ca9", refund.getTransactionReference());
        assertEquals("2025-07-15T07:20:00Z", Instant.ofEpochSecond(refund.getCreatedAt().getSeconds()).toString());
        assertThrows(AppException.class,
                () -> provider.get(GetRefundRequest.newBuilder().setRefundId("unknown").build()));
    }

    private Timestamp timestamp(Instant instant) {
        return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    }
}
