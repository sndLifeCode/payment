package com.example.settlement.api.settlement.controller;

import com.example.settlement.api.common.dto.PageResponse;
import com.example.settlement.api.settlement.dto.SettlementSearchItem;
import com.example.settlement.api.settlement.service.SettlementQueryService;
import com.example.settlement.core.domain.TransactionType;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/settlements")
public class SettlementController {

    private final SettlementQueryService settlementQueryService;

    public SettlementController(SettlementQueryService settlementQueryService) {
        this.settlementQueryService = settlementQueryService;
    }

    @Operation(summary = "정산 내역 조회")
    @GetMapping
    public PageResponse<SettlementSearchItem> search(
            @RequestParam @NotBlank String merchantId,
            @RequestParam LocalDate fromDate,
            @RequestParam LocalDate toDate,
            @RequestParam(required = false) TransactionType transactionType,
            @RequestParam(required = false) BigDecimal amount,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<SettlementSearchItem> result =
                settlementQueryService
                        .search(merchantId, fromDate, toDate, transactionType, amount, pageable)
                        .map(SettlementSearchItem::from);

        return PageResponse.from(result);
    }
}
