package com.tms.gateway.modules.grpc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tms.gateway.core.exception.AppException;
import com.tms.gateway.grpc.transaction.GetRefundRequest;
import com.tms.gateway.grpc.transaction.ListRefundsRequest;
import com.tms.gateway.grpc.transaction.ListRefundsResponse;
import com.tms.gateway.grpc.transaction.Refund;
import com.tms.gateway.grpc.transaction.TransactionServiceGrpc;
import com.tms.gateway.modules.grpc.config.GrpcProperties;
import io.grpc.Status;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TransactionGrpcClientRefundTest {

    private final TransactionServiceGrpc.TransactionServiceBlockingStub stub = mock(
            TransactionServiceGrpc.TransactionServiceBlockingStub.class);
    private final TransactionGrpcClient client = new TransactionGrpcClient(new GrpcProperties(), new ObjectMapper());

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(client, "stub", stub);
        when(stub.withDeadlineAfter(30, TimeUnit.SECONDS)).thenReturn(stub);
    }

    @Test
    void listRefundsForwardsRequest() {
        ListRefundsRequest request = ListRefundsRequest.newBuilder().setMerchantUserId(42L).setPage(2).build();
        ListRefundsResponse response = ListRefundsResponse.newBuilder().setPage(2).build();
        when(stub.listRefunds(any())).thenReturn(response);

        assertSame(response, client.listRefunds(request));
        verify(stub).listRefunds(request);
    }

    @Test
    void getRefundMapsNotFound() {
        GetRefundRequest request = GetRefundRequest.newBuilder().setMerchantUserId(42L).setRefundId("refund-1")
                .build();
        when(stub.getRefund(any())).thenThrow(Status.NOT_FOUND.asRuntimeException());

        AppException exception = assertThrows(AppException.class, () -> client.getRefund(request));

        assertEquals(404, exception.getStatus().value());
    }

    @Test
    void getRefundForwardsRequest() {
        GetRefundRequest request = GetRefundRequest.newBuilder().setMerchantUserId(42L).setRefundId("refund-1")
                .build();
        Refund response = Refund.newBuilder().setRefundId("refund-1").build();
        when(stub.getRefund(any())).thenReturn(response);

        assertSame(response, client.getRefund(request));
        verify(stub).getRefund(request);
    }
}
