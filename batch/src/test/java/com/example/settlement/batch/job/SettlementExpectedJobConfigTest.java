package com.example.settlement.batch.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.settlement.core.domain.PaymentMethod;
import com.example.settlement.core.domain.TransactionType;
import com.example.settlement.core.domain.exception.InvalidTransactionException;
import com.example.settlement.core.domain.exception.PolicyNotFoundException;
import com.example.settlement.core.domain.service.SettlementCalculationService;
import com.example.settlement.core.persistence.entity.PaymentTransaction;
import com.example.settlement.core.persistence.entity.SettlementExpected;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.ItemProcessor;

@ExtendWith(MockitoExtension.class)
class SettlementExpectedJobConfigTest {

    @Mock private SettlementCalculationService settlementCalculationService;

    private final SettlementExpectedJobConfig settlementExpectedJobConfig = new SettlementExpectedJobConfig();

    @Test
    @DisplayName("정책 없음 예외는 skip 처리한다")
    void settlementProcessorSkipsPolicyNotFoundException() throws Exception {
        ItemProcessor<PaymentTransaction, SettlementExpected> processor =
                settlementExpectedJobConfig.settlementProcessor("2026-03-10", settlementCalculationService);
        PaymentTransaction transaction = sampleTransaction();

        when(settlementCalculationService.calculateExpected(LocalDate.parse("2026-03-10"), transaction))
                .thenThrow(new PolicyNotFoundException("적용 가능한 정책이 없습니다."));

        SettlementExpected result = processor.process(transaction);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("잘못된 거래 예외는 skip 처리한다")
    void settlementProcessorSkipsInvalidTransactionException() throws Exception {
        ItemProcessor<PaymentTransaction, SettlementExpected> processor =
                settlementExpectedJobConfig.settlementProcessor("2026-03-10", settlementCalculationService);
        PaymentTransaction transaction = sampleTransaction();

        when(settlementCalculationService.calculateExpected(LocalDate.parse("2026-03-10"), transaction))
                .thenThrow(new InvalidTransactionException("원거래를 찾을 수 없습니다."));

        SettlementExpected result = processor.process(transaction);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("예상하지 못한 런타임 예외는 skip하지 않고 전파한다")
    void settlementProcessorPropagatesUnexpectedRuntimeException() {
        ItemProcessor<PaymentTransaction, SettlementExpected> processor =
                settlementExpectedJobConfig.settlementProcessor("2026-03-10", settlementCalculationService);
        PaymentTransaction transaction = sampleTransaction();

        when(settlementCalculationService.calculateExpected(LocalDate.parse("2026-03-10"), transaction))
                .thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> processor.process(transaction))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("db down");
    }

    private PaymentTransaction sampleTransaction() {
        return new PaymentTransaction(
                "tx-100",
                "PG1",
                "merchant1",
                PaymentMethod.CARD,
                TransactionType.APPROVAL,
                new BigDecimal("10000"),
                LocalDate.parse("2026-03-10"),
                null);
    }
}
