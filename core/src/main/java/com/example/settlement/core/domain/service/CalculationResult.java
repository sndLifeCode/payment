package com.example.settlement.core.domain.service;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CalculationResult(
        BigDecimal feeAmount,
        BigDecimal vatAmount,
        BigDecimal expectedSettlementAmount,
        LocalDate expectedSettlementDate) {}
