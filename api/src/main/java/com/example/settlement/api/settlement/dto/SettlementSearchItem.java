package com.example.settlement.api.settlement.dto;

import com.example.settlement.core.domain.TransactionType;
import com.example.settlement.core.persistence.entity.SettlementExpected;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SettlementSearchItem(
        String merchantId,
        LocalDate transactionDate,
        TransactionType transactionType,
        BigDecimal amount,
        BigDecimal expectedSettlementAmount,
        LocalDate expectedSettlementDate) {

    public static SettlementSearchItem from(SettlementExpected settlementExpected) {
        return new SettlementSearchItem(
                settlementExpected.getMerchantId(),
                settlementExpected.getTransactionDate(),
                settlementExpected.getTransactionType(),
                settlementExpected.getAmount(),
                settlementExpected.getExpectedSettlementAmount(),
                settlementExpected.getExpectedSettlementDate());
    }
}
