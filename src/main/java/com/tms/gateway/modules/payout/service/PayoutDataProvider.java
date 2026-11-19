package com.tms.gateway.modules.payout.service;

import java.util.List;

interface PayoutDataProvider {

    List<PayoutRecord> list(Long userId);

    PayoutRecord get(Long userId, String payoutId);
}
