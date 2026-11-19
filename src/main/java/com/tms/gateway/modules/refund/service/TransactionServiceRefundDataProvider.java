package com.tms.gateway.modules.refund.service;

import com.tms.gateway.grpc.transaction.GetRefundRequest;
import com.tms.gateway.grpc.transaction.ListRefundsRequest;
import com.tms.gateway.grpc.transaction.ListRefundsResponse;
import com.tms.gateway.grpc.transaction.Refund;
import com.tms.gateway.modules.grpc.service.TransactionGrpcClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "refunds.mock.enabled", havingValue = "false", matchIfMissing = true)
public class TransactionServiceRefundDataProvider implements RefundDataProvider {

    private final TransactionGrpcClient transactionGrpcClient;

    @Override
    public ListRefundsResponse list(ListRefundsRequest request) {
        return transactionGrpcClient.listRefunds(request);
    }

    @Override
    public Refund get(GetRefundRequest request) {
        return transactionGrpcClient.getRefund(request);
    }
}
