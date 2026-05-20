package com.example.settlement.api.policy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record UpdateFeePolicyRequest(
        @NotNull @Schema(example = "2026-12-31") LocalDate endDate) {}
