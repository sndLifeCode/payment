package com.example.settlement.core.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.settlement.core.domain.FeeType;
import com.example.settlement.core.domain.PaymentMethod;
import com.example.settlement.core.domain.TransactionType;
import com.example.settlement.core.persistence.entity.FeePolicy;
import com.example.settlement.core.persistence.entity.PaymentTransaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettlementCalculatorTest {

    private final SettlementCalculator settlementCalculator = new SettlementCalculator();

    @Test
    @DisplayName("정률 승인 수수료와 부가세를 계산한다")
    void calculateApprovalRateFee() {
        FeePolicy policy =
                new FeePolicy(
                        "PG1",
                        "merchant1",
                        PaymentMethod.CARD,
                        FeeType.RATE,
                        new BigDecimal("0.0206"),
                        2,
                        LocalDate.parse("2026-01-01"),
                        LocalDate.parse("2026-12-31"));

        PaymentTransaction tx =
                new PaymentTransaction(
                        "tx-1",
                        "PG1",
                        "merchant1",
                        PaymentMethod.CARD,
                        TransactionType.APPROVAL,
                        new BigDecimal("100000"),
                        LocalDate.parse("2026-03-10"),
                        null);

        CalculationResult result = settlementCalculator.calculateApproval(tx, policy);

        assertThat(result.feeAmount()).isEqualByComparingTo("2060");
        assertThat(result.vatAmount()).isEqualByComparingTo("206");
        assertThat(result.expectedSettlementAmount()).isEqualByComparingTo("97734");
        assertThat(result.expectedSettlementDate()).isEqualTo(LocalDate.parse("2026-03-12"));
    }

    @Test
    @DisplayName("정액 승인 수수료와 부가세를 계산한다")
    void calculateApprovalFixedFee() {
        FeePolicy policy =
                new FeePolicy(
                        "PG1",
                        "merchant1",
                        PaymentMethod.BANK_TRANSFER,
                        FeeType.FIXED,
                        new BigDecimal("250"),
                        1,
                        LocalDate.parse("2026-01-01"),
                        LocalDate.parse("2026-12-31"));

        PaymentTransaction tx =
                new PaymentTransaction(
                        "tx-2",
                        "PG1",
                        "merchant1",
                        PaymentMethod.BANK_TRANSFER,
                        TransactionType.APPROVAL,
                        new BigDecimal("100000"),
                        LocalDate.parse("2026-03-10"),
                        null);

        CalculationResult result = settlementCalculator.calculateApproval(tx, policy);

        assertThat(result.feeAmount()).isEqualByComparingTo("250");
        assertThat(result.vatAmount()).isEqualByComparingTo("25");
        assertThat(result.expectedSettlementAmount()).isEqualByComparingTo("99725");
        assertThat(result.expectedSettlementDate()).isEqualTo(LocalDate.parse("2026-03-11"));
    }

    @Test
    @DisplayName("정액 부분취소는 수수료 환급이 없다")
    void cancelPartialFixedNoRefund() {
        FeePolicy originalPolicy =
                new FeePolicy(
                        "PG1",
                        "merchant1",
                        PaymentMethod.BANK_TRANSFER,
                        FeeType.FIXED,
                        new BigDecimal("250"),
                        1,
                        LocalDate.parse("2026-01-01"),
                        LocalDate.parse("2026-12-31"));

        PaymentTransaction originalApproval =
                new PaymentTransaction(
                        "tx-100",
                        "PG1",
                        "merchant1",
                        PaymentMethod.BANK_TRANSFER,
                        TransactionType.APPROVAL,
                        new BigDecimal("100000"),
                        LocalDate.parse("2026-03-10"),
                        null);

        PaymentTransaction cancel =
                new PaymentTransaction(
                        "tx-101",
                        "PG1",
                        "merchant1",
                        PaymentMethod.BANK_TRANSFER,
                        TransactionType.CANCEL,
                        new BigDecimal("40000"),
                        LocalDate.parse("2026-03-10"),
                        "tx-100");

        CalculationResult result =
                settlementCalculator.calculateCancellation(
                        cancel, originalApproval, originalPolicy, BigDecimal.ZERO);

        assertThat(result.feeAmount()).isEqualByComparingTo("0");
        assertThat(result.vatAmount()).isEqualByComparingTo("0");
        assertThat(result.expectedSettlementAmount()).isEqualByComparingTo("-40000");
    }

    @Test
    @DisplayName("정액 누적취소가 전액취소에 도달하면 수수료를 환급한다")
    void cancelFullFixedRefund() {
        FeePolicy originalPolicy =
                new FeePolicy(
                        "PG1",
                        "merchant1",
                        PaymentMethod.BANK_TRANSFER,
                        FeeType.FIXED,
                        new BigDecimal("250"),
                        1,
                        LocalDate.parse("2026-01-01"),
                        LocalDate.parse("2026-12-31"));

        PaymentTransaction originalApproval =
                new PaymentTransaction(
                        "tx-100",
                        "PG1",
                        "merchant1",
                        PaymentMethod.BANK_TRANSFER,
                        TransactionType.APPROVAL,
                        new BigDecimal("100000"),
                        LocalDate.parse("2026-03-10"),
                        null);

        PaymentTransaction cancel =
                new PaymentTransaction(
                        "tx-102",
                        "PG1",
                        "merchant1",
                        PaymentMethod.BANK_TRANSFER,
                        TransactionType.CANCEL,
                        new BigDecimal("60000"),
                        LocalDate.parse("2026-03-11"),
                        "tx-100");

        CalculationResult result =
                settlementCalculator.calculateCancellation(
                        cancel, originalApproval, originalPolicy, new BigDecimal("40000"));

        assertThat(result.feeAmount()).isEqualByComparingTo("-250");
        assertThat(result.vatAmount()).isEqualByComparingTo("-25");
        assertThat(result.expectedSettlementAmount()).isEqualByComparingTo("-59725");
    }

    @Test
    @DisplayName("정률 취소는 취소 금액 비례로 수수료를 환급한다")
    void cancelRateRefundProportional() {
        FeePolicy originalPolicy =
                new FeePolicy(
                        "PG1",
                        "merchant1",
                        PaymentMethod.CARD,
                        FeeType.RATE,
                        new BigDecimal("0.0206"),
                        2,
                        LocalDate.parse("2026-01-01"),
                        LocalDate.parse("2026-12-31"));

        PaymentTransaction originalApproval =
                new PaymentTransaction(
                        "tx-100",
                        "PG1",
                        "merchant1",
                        PaymentMethod.CARD,
                        TransactionType.APPROVAL,
                        new BigDecimal("100000"),
                        LocalDate.parse("2026-03-10"),
                        null);

        PaymentTransaction cancel =
                new PaymentTransaction(
                        "tx-103",
                        "PG1",
                        "merchant1",
                        PaymentMethod.CARD,
                        TransactionType.CANCEL,
                        new BigDecimal("30000"),
                        LocalDate.parse("2026-03-10"),
                        "tx-100");

        CalculationResult result =
                settlementCalculator.calculateCancellation(
                        cancel, originalApproval, originalPolicy, BigDecimal.ZERO);

        assertThat(result.feeAmount()).isEqualByComparingTo("-618");
        assertThat(result.vatAmount()).isEqualByComparingTo("-61");
        assertThat(result.expectedSettlementAmount()).isEqualByComparingTo("-29321");
    }
}
