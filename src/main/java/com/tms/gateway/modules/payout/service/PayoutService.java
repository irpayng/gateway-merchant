package com.tms.gateway.modules.payout.service;

import com.tms.gateway.modules.grpc.service.TransactionGrpcClient;
import com.tms.gateway.modules.payout.dto.NameEnquiryResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for merchant payout operations. Provides bank account verification
 * before initiating payouts to customers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutService {

    private final TransactionGrpcClient transactionClient;

    /**
     * Verify a bank account holder's name via NIP/NIBSS name enquiry.
     *
     * <p>
     * This should be called before initiating a payout to ensure the account number
     * belongs to the intended recipient.
     *
     * @param accountNumber
     *            10-digit bank account number
     * @param bankCode
     *            CBN bank code (e.g., "000013" for GTBank)
     * @return the account holder's name and details
     */
    public NameEnquiryResponse nameEnquiry(String accountNumber, String bankCode) {
        log.info("Processing name enquiry: account={} bank={}", accountNumber, bankCode);

        Map<String, Object> result = transactionClient.nameEnquiry(accountNumber, bankCode);

        NameEnquiryResponse response = NameEnquiryResponse.fromMap(result);
        log.info("Name enquiry successful: account={} name={}", accountNumber, response.accountName());

        return response;
    }
}
