package com.example.settlement.api.policy.controller;

import com.example.settlement.api.common.dto.PageResponse;
import com.example.settlement.api.policy.dto.CreateFeePolicyRequest;
import com.example.settlement.api.policy.dto.FeePolicyResponse;
import com.example.settlement.api.policy.dto.UpdateFeePolicyRequest;
import com.example.settlement.api.policy.service.FeePolicyService;
import com.example.settlement.core.domain.PaymentMethod;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/policies")
public class FeePolicyController {

    private final FeePolicyService feePolicyService;

    public FeePolicyController(FeePolicyService feePolicyService) {
        this.feePolicyService = feePolicyService;
    }

    @Operation(summary = "정산 수수료 정책 등록")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FeePolicyResponse create(@Valid @RequestBody CreateFeePolicyRequest request) {
        return FeePolicyResponse.from(feePolicyService.create(request));
    }

    @Operation(summary = "정산 수수료 정책 조회")
    @GetMapping
    public PageResponse<FeePolicyResponse> search(
            @RequestParam(required = false) String pgCompany,
            @RequestParam(required = false) String merchantId,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(required = false) LocalDate targetDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<FeePolicyResponse> result =
                feePolicyService
                        .search(pgCompany, merchantId, paymentMethod, targetDate, pageable)
                        .map(FeePolicyResponse::from);
        return PageResponse.from(result);
    }

    @Operation(summary = "정산 수수료 정책 변경")
    @PutMapping("/{policyId}")
    public FeePolicyResponse update(
            @PathVariable Long policyId, @Valid @RequestBody UpdateFeePolicyRequest request) {
        return FeePolicyResponse.from(feePolicyService.update(policyId, request));
    }
}
