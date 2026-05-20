package com.example.settlement.core.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.settlement.core.domain.FeeType;
import com.example.settlement.core.domain.PaymentMethod;
import com.example.settlement.core.domain.TransactionType;
import com.example.settlement.core.domain.exception.InvalidTransactionException;
import com.example.settlement.core.domain.exception.PolicyNotFoundException;
import com.example.settlement.core.persistence.entity.FeePolicy;
import com.example.settlement.core.persistence.entity.PaymentTransaction;
import com.example.settlement.core.persistence.entity.SettlementExpected;
import com.example.settlement.core.persistence.repository.FeePolicyRepository;
import com.example.settlement.core.persistence.repository.PaymentTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class SettlementCalculationServiceTest {

    @Mock private FeePolicyRepository feePolicyRepository;
    @Mock private PaymentTransactionRepository paymentTransactionRepository;

    private SettlementCalculationService settlementCalculationService;

    @BeforeEach
    void setUp() {
        settlementCalculationService =
                new SettlementCalculationService(
                        feePolicyRepository,
                        paymentTransactionRepository,
                        new SettlementCalculator());
    }

    @Test
    @DisplayName("승인 거래는 거래일 기준 정책으로 정산 예정금액을 계산한다")
    void calculateApprovalUsesTransactionDatePolicy() {
        LocalDate baseDate = LocalDate.parse("2026-03-10");
        PaymentTransaction approval =
                new PaymentTransaction(
                        "tx-approval-1",
                        "PG1",
                        "merchant1",
                        PaymentMethod.CARD,
                        TransactionType.APPROVAL,
                        new BigDecimal("100000"),
                        LocalDate.parse("2026-03-10"),
                        null);

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

        when(feePolicyRepository.findActivePolicy(
                        "PG1",
                        "merchant1",
                        PaymentMethod.CARD,
                        LocalDate.parse("2026-03-10"),
                        PageRequest.of(0, 1)))
                .thenReturn(new PageImpl<>(java.util.List.of(policy)));

        SettlementExpected actual = settlementCalculationService.calculateExpected(baseDate, approval);

        assertThat(actual.getBaseDate()).isEqualTo(baseDate);
        assertThat(actual.getPgCompany()).isEqualTo("PG1");
        assertThat(actual.getTransactionId()).isEqualTo("tx-approval-1");
        assertThat(actual.getFeeAmount()).isEqualByComparingTo("2060");
        assertThat(actual.getVatAmount()).isEqualByComparingTo("206");
        assertThat(actual.getExpectedSettlementAmount()).isEqualByComparingTo("97734");
        assertThat(actual.getExpectedSettlementDate()).isEqualTo(LocalDate.parse("2026-03-12"));

        verifyNoInteractions(paymentTransactionRepository);
    }

    @Test
    @DisplayName("취소 거래는 원거래 승인 시점 정책과 이전 취소 누적금액으로 계산한다")
    void calculateCancelUsesOriginalApprovalPolicyAndCanceledAmountBefore() {
        LocalDate baseDate = LocalDate.parse("2026-03-11");
        PaymentTransaction cancel =
                new PaymentTransaction(
                        "tx-cancel-1",
                        "PG1",
                        "merchant1",
                        PaymentMethod.BANK_TRANSFER,
                        TransactionType.CANCEL,
                        new BigDecimal("60000"),
                        LocalDate.parse("2026-03-11"),
                        "tx-origin-1");

        PaymentTransaction originalApproval =
                new PaymentTransaction(
                        "tx-origin-1",
                        "PG1",
                        "merchant1",
                        PaymentMethod.BANK_TRANSFER,
                        TransactionType.APPROVAL,
                        new BigDecimal("100000"),
                        LocalDate.parse("2026-03-10"),
                        null);

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

        when(paymentTransactionRepository.findByTransactionId("tx-origin-1"))
                .thenReturn(Optional.of(originalApproval));
        when(paymentTransactionRepository.sumCanceledAmountBefore("tx-origin-1", Long.MAX_VALUE))
                .thenReturn(new BigDecimal("40000"));
        when(feePolicyRepository.findActivePolicy(
                        "PG1",
                        "merchant1",
                        PaymentMethod.BANK_TRANSFER,
                        LocalDate.parse("2026-03-10"),
                        PageRequest.of(0, 1)))
                .thenReturn(new PageImpl<>(java.util.List.of(originalPolicy)));

        SettlementExpected actual = settlementCalculationService.calculateExpected(baseDate, cancel);

        assertThat(actual.getBaseDate()).isEqualTo(baseDate);
        assertThat(actual.getTransactionId()).isEqualTo("tx-cancel-1");
        assertThat(actual.getFeeAmount()).isEqualByComparingTo("-250");
        assertThat(actual.getVatAmount()).isEqualByComparingTo("-25");
        assertThat(actual.getExpectedSettlementAmount()).isEqualByComparingTo("-59725");
        assertThat(actual.getExpectedSettlementDate()).isEqualTo(LocalDate.parse("2026-03-12"));

        verify(paymentTransactionRepository).findByTransactionId("tx-origin-1");
        verify(paymentTransactionRepository).sumCanceledAmountBefore("tx-origin-1", Long.MAX_VALUE);
    }

    @Test
    @DisplayName("취소 거래에 originalTransactionId가 없으면 예외가 발생한다")
    void calculateCancelWithoutOriginalTransactionIdThrows() {
        PaymentTransaction cancel =
                new PaymentTransaction(
                        "tx-cancel-1",
                        "PG1",
                        "merchant1",
                        PaymentMethod.CARD,
                        TransactionType.CANCEL,
                        new BigDecimal("10000"),
                        LocalDate.parse("2026-03-11"),
                        " ");

        assertThatThrownBy(
                        () ->
                                settlementCalculationService.calculateExpected(
                                        LocalDate.parse("2026-03-11"), cancel))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("originalTransactionId");

        verifyNoInteractions(feePolicyRepository);
    }

    @Test
    @DisplayName("적용 가능한 정책이 없으면 예외가 발생한다")
    void calculateApprovalWithoutPolicyThrows() {
        PaymentTransaction approval =
                new PaymentTransaction(
                        "tx-approval-1",
                        "PG1",
                        "merchant1",
                        PaymentMethod.CARD,
                        TransactionType.APPROVAL,
                        new BigDecimal("100000"),
                        LocalDate.parse("2026-03-10"),
                        null);

        when(feePolicyRepository.findActivePolicy(any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        assertThatThrownBy(
                        () ->
                                settlementCalculationService.calculateExpected(
                                        LocalDate.parse("2026-03-10"), approval))
                .isInstanceOf(PolicyNotFoundException.class)
                .hasMessageContaining("merchant1");
    }
}
