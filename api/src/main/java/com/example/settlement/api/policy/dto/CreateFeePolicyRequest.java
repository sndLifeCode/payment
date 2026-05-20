package com.example.settlement.api.policy.dto;

import com.example.settlement.core.domain.FeeType;
import com.example.settlement.core.domain.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateFeePolicyRequest(
        @NotBlank @Schema(example = "PG1") String pgCompany,
        @NotBlank @Schema(example = "merchant1") String merchantId,
        @NotNull PaymentMethod paymentMethod,
        @NotNull FeeType feeType,
        @NotNull @Positive @Schema(example = "0.0206") BigDecimal feeValue,
        @NotNull @Min(0) @Schema(example = "1") Integer settlementCycleDays,
        @NotNull @Schema(example = "2026-01-01") LocalDate startDate,
        @NotNull @Schema(example = "2026-12-31") LocalDate endDate) {}
