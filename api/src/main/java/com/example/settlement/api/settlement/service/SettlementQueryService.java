package com.example.settlement.api.settlement.service;

import com.example.settlement.core.domain.TransactionType;
import com.example.settlement.core.persistence.entity.SettlementExpected;
import com.example.settlement.core.persistence.repository.SettlementExpectedRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementQueryService {

    private static final long MAX_QUERY_DAYS = 31;

    private final SettlementExpectedRepository settlementExpectedRepository;

    public SettlementQueryService(SettlementExpectedRepository settlementExpectedRepository) {
        this.settlementExpectedRepository = settlementExpectedRepository;
    }

    @Transactional(readOnly = true)
    public Page<SettlementExpected> search(
            String merchantId,
            LocalDate fromDate,
            LocalDate toDate,
            TransactionType transactionType,
            BigDecimal amount,
            Pageable pageable) {

        if (toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("toDate는 fromDate보다 빠를 수 없습니다.");
        }

        long between = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        if (between > MAX_QUERY_DAYS) {
            throw new IllegalArgumentException("조회 기간은 최대 31일입니다.");
        }

        return settlementExpectedRepository.search(
                merchantId, fromDate, toDate, transactionType, amount, pageable);
    }
}
