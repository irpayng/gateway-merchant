package com.tms.gateway.modules.payout.service;

import java.math.BigDecimal;
import java.time.Instant;

record PayoutRecord(String payoutId, String paymentReference, BigDecimal totalValue, BigDecimal payoutValue,
        String currencyCode, String bankName, String accountName, String accountNumber, String status, Instant salesDate,
        Instant dateTimeSettled) {
}
