package com.example.settlement.core.persistence.entity;

import com.example.settlement.core.domain.FeeType;
import com.example.settlement.core.domain.PaymentMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "fee_policy",
        indexes = {
            @Index(
                    name = "idx_fee_policy_lookup",
                    columnList = "pg_company, merchant_id, payment_method, start_date, end_date")
        })
public class FeePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pg_company", nullable = false, length = 50)
    private String pgCompany;

    @Column(name = "merchant_id", nullable = false, length = 100)
    private String merchantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 50)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_type", nullable = false, length = 20)
    private FeeType feeType;

    @Column(name = "fee_value", nullable = false, precision = 18, scale = 6)
    private BigDecimal feeValue;

    @Column(name = "settlement_cycle_days", nullable = false)
    private Integer settlementCycleDays;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected FeePolicy() {}

    public FeePolicy(
            String pgCompany,
            String merchantId,
            PaymentMethod paymentMethod,
            FeeType feeType,
            BigDecimal feeValue,
            Integer settlementCycleDays,
            LocalDate startDate,
            LocalDate endDate) {
        this.pgCompany = pgCompany;
        this.merchantId = merchantId;
        this.paymentMethod = paymentMethod;
        this.feeType = feeType;
        this.feeValue = feeValue;
        this.settlementCycleDays = settlementCycleDays;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isEffectiveOn(LocalDate transactionDate) {
        return !transactionDate.isBefore(startDate) && !transactionDate.isAfter(endDate);
    }

    public void updateEndDate(LocalDate newEndDate) {
        this.endDate = newEndDate;
    }

    public Long getId() {
        return id;
    }

    public String getPgCompany() {
        return pgCompany;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public FeeType getFeeType() {
        return feeType;
    }

    public BigDecimal getFeeValue() {
        return feeValue;
    }

    public Integer getSettlementCycleDays() {
        return settlementCycleDays;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
