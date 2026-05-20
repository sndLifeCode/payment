package com.example.settlement.api.policy.service;

import com.example.settlement.api.policy.dto.CreateFeePolicyRequest;
import com.example.settlement.api.policy.dto.UpdateFeePolicyRequest;
import com.example.settlement.core.domain.exception.PolicyConflictException;
import com.example.settlement.core.domain.exception.PolicyNotFoundException;
import com.example.settlement.core.persistence.entity.FeePolicy;
import com.example.settlement.core.persistence.repository.FeePolicyRepository;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeePolicyService {

    private final FeePolicyRepository feePolicyRepository;

    public FeePolicyService(FeePolicyRepository feePolicyRepository) {
        this.feePolicyRepository = feePolicyRepository;
    }

    @Transactional
    public FeePolicy create(CreateFeePolicyRequest request) {
        validateDateRange(request.startDate(), request.endDate());

        boolean overlapped =
                feePolicyRepository.existsOverlappedPolicy(
                        request.pgCompany(),
                        request.merchantId(),
                        request.paymentMethod(),
                        request.startDate(),
                        request.endDate(),
                        null);

        if (overlapped) {
            throw new PolicyConflictException("수수료 정책 기간이 중복됩니다.");
        }

        FeePolicy policy =
                new FeePolicy(
                        request.pgCompany(),
                        request.merchantId(),
                        request.paymentMethod(),
                        request.feeType(),
                        request.feeValue(),
                        request.settlementCycleDays(),
                        request.startDate(),
                        request.endDate());
        return feePolicyRepository.save(policy);
    }

    @Transactional
    public FeePolicy update(Long policyId, UpdateFeePolicyRequest request) {
        FeePolicy feePolicy =
                feePolicyRepository
                        .findById(policyId)
                        .orElseThrow(
                                () ->
                                        new PolicyNotFoundException(
                                                "수수료 정책이 존재하지 않습니다. policyId="
                                                        + policyId));

        validateDateRange(feePolicy.getStartDate(), request.endDate());

        boolean overlapped =
                feePolicyRepository.existsOverlappedPolicy(
                        feePolicy.getPgCompany(),
                        feePolicy.getMerchantId(),
                        feePolicy.getPaymentMethod(),
                        feePolicy.getStartDate(),
                        request.endDate(),
                        feePolicy.getId());

        if (overlapped) {
            throw new PolicyConflictException("정책 변경 결과 기간이 중복됩니다.");
        }

        feePolicy.updateEndDate(request.endDate());
        return feePolicy;
    }

    @Transactional(readOnly = true)
    public Page<FeePolicy> search(
            String pgCompany,
            String merchantId,
            com.example.settlement.core.domain.PaymentMethod paymentMethod,
            LocalDate targetDate,
            Pageable pageable) {
        return feePolicyRepository.search(pgCompany, merchantId, paymentMethod, targetDate, pageable);
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate는 startDate보다 빠를 수 없습니다.");
        }
    }
}
