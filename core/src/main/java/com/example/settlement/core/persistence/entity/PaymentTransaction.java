package com.example.settlement.core.persistence.entity;

import com.example.settlement.core.domain.PaymentMethod;
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
        name = "payment_transaction",
        indexes = {
            @Index(name = "idx_tx_pg_date", columnList = "pg_company, transaction_date"),
            @Index(name = "idx_tx_merchant_date", columnList = "merchant_id, transaction_date"),
            @Index(name = "idx_tx_original_tx", columnList = "original_transaction_id")
        })
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, unique = true, length = 100)
    private String transactionId;

    @Column(name = "pg_company", nullable = false, length = 50)
    private String pgCompany;

    @Column(name = "merchant_id", nullable = false, length = 100)
    private String merchantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 50)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    @Column(name = "amount", nullable = false, precision = 18, scale = 0)
    private BigDecimal amount;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "original_transaction_id", length = 100)
    private String originalTransactionId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected PaymentTransaction() {}

    public PaymentTransaction(
            String transactionId,
            String pgCompany,
            String merchantId,
            PaymentMethod paymentMethod,
            TransactionType transactionType,
            BigDecimal amount,
            LocalDate transactionDate,
            String originalTransactionId) {
        this.transactionId = transactionId;
        this.pgCompany = pgCompany;
        this.merchantId = merchantId;
        this.paymentMethod = paymentMethod;
        this.transactionType = transactionType;
        this.amount = amount;
        this.transactionDate = transactionDate;
        this.originalTransactionId = originalTransactionId;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getTransactionId() {
        return transactionId;
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

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public String getOriginalTransactionId() {
        return originalTransactionId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
