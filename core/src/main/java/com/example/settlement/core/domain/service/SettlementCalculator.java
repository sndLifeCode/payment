package com.example.settlement.core.domain.service;

import com.example.settlement.core.domain.FeeType;
import com.example.settlement.core.persistence.entity.FeePolicy;
import com.example.settlement.core.persistence.entity.PaymentTransaction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class SettlementCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal VAT_RATE = new BigDecimal("0.1");

    public CalculationResult calculateApproval(PaymentTransaction transaction, FeePolicy policy) {
        BigDecimal feeAmount = calculateFee(policy, transaction.getAmount());
        BigDecimal vatAmount = calculateVat(feeAmount);
        BigDecimal expected = transaction.getAmount().subtract(feeAmount).subtract(vatAmount);
        LocalDate expectedDate =
                transaction.getTransactionDate().plusDays(policy.getSettlementCycleDays());

        return new CalculationResult(feeAmount, vatAmount, expected, expectedDate);
    }

    public CalculationResult calculateCancellation(
            PaymentTransaction cancelTransaction,
            PaymentTransaction originalApprovalTransaction,
            FeePolicy originalPolicy,
            BigDecimal canceledAmountBeforeCurrent) {

        BigDecimal feeAmount = BigDecimal.ZERO;
        BigDecimal vatAmount = BigDecimal.ZERO;

        if (originalPolicy.getFeeType() == FeeType.RATE) {
            BigDecimal feeRefund = calculateFee(originalPolicy, cancelTransaction.getAmount());
            feeAmount = feeRefund.negate();
            vatAmount = calculateVat(feeRefund).negate();
        } else {
            BigDecimal canceledAfterCurrent = canceledAmountBeforeCurrent.add(cancelTransaction.getAmount());
            boolean isFullCancellation =
                    canceledAfterCurrent.compareTo(originalApprovalTransaction.getAmount()) >= 0;
            if (isFullCancellation) {
                feeAmount = originalPolicy.getFeeValue().setScale(0, RoundingMode.DOWN).negate();
                vatAmount = calculateVat(originalPolicy.getFeeValue()).negate();
            }
        }

        BigDecimal expectedSettlementAmount =
                cancelTransaction.getAmount().negate().subtract(feeAmount).subtract(vatAmount);
        LocalDate expectedDate =
                cancelTransaction.getTransactionDate().plusDays(originalPolicy.getSettlementCycleDays());

        return new CalculationResult(feeAmount, vatAmount, expectedSettlementAmount, expectedDate);
    }

    private BigDecimal calculateFee(FeePolicy policy, BigDecimal amount) {
        if (policy.getFeeType() == FeeType.FIXED) {
            return policy.getFeeValue().setScale(0, RoundingMode.DOWN);
        }

        BigDecimal normalizedRate = normalizeRate(policy.getFeeValue());
        return amount.multiply(normalizedRate).setScale(0, RoundingMode.DOWN);
    }

    private BigDecimal normalizeRate(BigDecimal rateValue) {
        if (rateValue.compareTo(BigDecimal.ONE) > 0) {
            return rateValue.divide(HUNDRED, 10, RoundingMode.HALF_UP);
        }
        return rateValue;
    }

    private BigDecimal calculateVat(BigDecimal feeAmount) {
        BigDecimal vat = feeAmount.abs().multiply(VAT_RATE).setScale(0, RoundingMode.DOWN);
        return feeAmount.signum() < 0 ? vat.negate() : vat;
    }
}
