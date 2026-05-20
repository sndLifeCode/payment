package com.example.settlement.core.persistence.entity;

import com.example.settlement.core.domain.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "settlement_expected",
        indexes = {
            @Index(name = "idx_settlement_merchant_date", columnList = "merchant_id, transaction_date"),
            @Index(name = "idx_settlement_transaction_date", columnList = "transaction_date"),
            @Index(name = "idx_settlement_base_pg", columnList = "base_date, pg_company")
        })
public class SettlementExpected {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "base_date", nullable = false)
    private LocalDate baseDate;

    @Column(name = "pg_company", nullable = false, length = 50)
    private String pgCompany;

    @Column(name = "transaction_id", nullable = false, length = 100)
    private String transactionId;

    @Column(name = "merchant_id", nullable = false, length = 100)
    private String merchantId;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    @Column(name = "amount", nullable = false, precision = 18, scale = 0)
    private BigDecimal amount;

    @Column(name = "fee_amount", nullable = false, precision = 18, scale = 0)
    private BigDecimal feeAmount;

    @Column(name = "vat_amount", nullable = false, precision = 18, scale = 0)
    private BigDecimal vatAmount;

    @Column(name = "expected_settlement_amount", nullable = false, precision = 18, scale = 0)
    private BigDecimal expectedSettlementAmount;

    @Column(name = "expected_settlement_date", nullable = false)
    private LocalDate expectedSettlementDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected SettlementExpected() {}

    public SettlementExpected(
            LocalDate baseDate,
            String pgCompany,
            String transactionId,
            String merchantId,
            LocalDate transactionDate,
            TransactionType transactionType,
            BigDecimal amount,
            BigDecimal feeAmount,
            BigDecimal vatAmount,
            BigDecimal expectedSettlementAmount,
            LocalDate expectedSettlementDate) {
        this.baseDate = baseDate;
        this.pgCompany = pgCompany;
        this.transactionId = transactionId;
        this.merchantId = merchantId;
        this.transactionDate = transactionDate;
        this.transactionType = transactionType;
        this.amount = amount;
        this.feeAmount = feeAmount;
        this.vatAmount = vatAmount;
        this.expectedSettlementAmount = expectedSettlementAmount;
        this.expectedSettlementDate = expectedSettlementDate;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public LocalDate getBaseDate() {
        return baseDate;
    }

    public String getPgCompany() {
        return pgCompany;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public BigDecimal getVatAmount() {
        return vatAmount;
    }

    public BigDecimal getExpectedSettlementAmount() {
        return expectedSettlementAmount;
    }

    public LocalDate getExpectedSettlementDate() {
        return expectedSettlementDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
