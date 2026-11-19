package com.tms.gateway.modules.refund.service;

import com.tms.gateway.grpc.transaction.GetRefundRequest;
import com.tms.gateway.grpc.transaction.ListRefundsRequest;
import com.tms.gateway.grpc.transaction.ListRefundsResponse;
import com.tms.gateway.grpc.transaction.Refund;

public interface RefundDataProvider {

    ListRefundsResponse list(ListRefundsRequest request);

    Refund get(GetRefundRequest request);
}
