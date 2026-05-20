package com.example.settlement.core.domain.service;

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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementCalculationService {

    private final FeePolicyRepository feePolicyRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SettlementCalculator settlementCalculator;

    public SettlementCalculationService(
            FeePolicyRepository feePolicyRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            SettlementCalculator settlementCalculator) {
        this.feePolicyRepository = feePolicyRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.settlementCalculator = settlementCalculator;
    }

    @Transactional(readOnly = true)
    public SettlementExpected calculateExpected(LocalDate baseDate, PaymentTransaction transaction) {
        if (transaction.getTransactionType() == TransactionType.APPROVAL) {
            return calculateApproval(baseDate, transaction);
        }
        return calculateCancel(baseDate, transaction);
    }

    private SettlementExpected calculateApproval(LocalDate baseDate, PaymentTransaction transaction) {
        FeePolicy policy =
                findPolicy(
                        transaction.getPgCompany(),
                        transaction.getMerchantId(),
                        transaction.getPaymentMethod(),
                        transaction.getTransactionDate());

        CalculationResult result = settlementCalculator.calculateApproval(transaction, policy);
        return toSettlementExpected(baseDate, transaction, result);
    }

    private SettlementExpected calculateCancel(LocalDate baseDate, PaymentTransaction cancelTransaction) {
        if (cancelTransaction.getOriginalTransactionId() == null
                || cancelTransaction.getOriginalTransactionId().isBlank()) {
            throw new InvalidTransactionException(
                    "취소 거래에는 originalTransactionId가 필요합니다. transactionId="
                            + cancelTransaction.getTransactionId());
        }

        PaymentTransaction originalApproval =
                paymentTransactionRepository
                        .findByTransactionId(cancelTransaction.getOriginalTransactionId())
                        .orElseThrow(
                                () ->
                                        new InvalidTransactionException(
                                                "원거래를 찾을 수 없습니다. originalTransactionId="
                                                        + cancelTransaction
                                                                .getOriginalTransactionId()));

        FeePolicy originalPolicy =
                findPolicy(
                        originalApproval.getPgCompany(),
                        originalApproval.getMerchantId(),
                        originalApproval.getPaymentMethod(),
                        originalApproval.getTransactionDate());

        Long currentId = cancelTransaction.getId() == null ? Long.MAX_VALUE : cancelTransaction.getId();
        BigDecimal canceledAmountBefore =
                paymentTransactionRepository.sumCanceledAmountBefore(
                        originalApproval.getTransactionId(), currentId);

        CalculationResult result =
                settlementCalculator.calculateCancellation(
                        cancelTransaction, originalApproval, originalPolicy, canceledAmountBefore);
        return toSettlementExpected(baseDate, cancelTransaction, result);
    }

    private FeePolicy findPolicy(
            String pgCompany,
            String merchantId,
            com.example.settlement.core.domain.PaymentMethod paymentMethod,
            LocalDate transactionDate) {
        return feePolicyRepository
                .findActivePolicy(
                        pgCompany,
                        merchantId,
                        paymentMethod,
                        transactionDate,
                        PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElseThrow(
                        () ->
                                new PolicyNotFoundException(
                                        "적용 가능한 수수료 정책이 없습니다. pgCompany="
                                                + pgCompany
                                                + ", merchantId="
                                                + merchantId
                                                + ", paymentMethod="
                                                + paymentMethod
                                                + ", transactionDate="
                                                + transactionDate));
    }

    private SettlementExpected toSettlementExpected(
            LocalDate baseDate, PaymentTransaction transaction, CalculationResult result) {
        return new SettlementExpected(
                baseDate,
                transaction.getPgCompany(),
                transaction.getTransactionId(),
                transaction.getMerchantId(),
                transaction.getTransactionDate(),
                transaction.getTransactionType(),
                transaction.getAmount(),
                result.feeAmount(),
                result.vatAmount(),
                result.expectedSettlementAmount(),
                result.expectedSettlementDate());
    }
}
