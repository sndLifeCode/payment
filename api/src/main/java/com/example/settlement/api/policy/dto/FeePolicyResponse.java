package com.example.settlement.api.policy.dto;

import com.example.settlement.core.domain.FeeType;
import com.example.settlement.core.domain.PaymentMethod;
import com.example.settlement.core.persistence.entity.FeePolicy;
import java.math.BigDecimal;
import java.time.LocalDate;

public record FeePolicyResponse(
        Long id,
        String pgCompany,
        String merchantId,
        PaymentMethod paymentMethod,
        FeeType feeType,
        BigDecimal feeValue,
        Integer settlementCycleDays,
        LocalDate startDate,
        LocalDate endDate) {

    public static FeePolicyResponse from(FeePolicy feePolicy) {
        return new FeePolicyResponse(
                feePolicy.getId(),
                feePolicy.getPgCompany(),
                feePolicy.getMerchantId(),
                feePolicy.getPaymentMethod(),
                feePolicy.getFeeType(),
                feePolicy.getFeeValue(),
                feePolicy.getSettlementCycleDays(),
                feePolicy.getStartDate(),
                feePolicy.getEndDate());
    }
}
