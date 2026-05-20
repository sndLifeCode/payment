package com.example.settlement.core.persistence.repository;

import com.example.settlement.core.persistence.entity.PaymentTransaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    List<PaymentTransaction> findByPgCompanyAndTransactionDateOrderByIdAsc(
            String pgCompany, LocalDate transactionDate);

    Optional<PaymentTransaction> findByTransactionId(String transactionId);

    @Query(
            """
            select coalesce(sum(pt.amount), 0)
            from PaymentTransaction pt
            where pt.originalTransactionId = :originalTransactionId
              and pt.transactionType = com.example.settlement.core.domain.TransactionType.CANCEL
              and pt.id < :currentId
            """)
    BigDecimal sumCanceledAmountBefore(
            @Param("originalTransactionId") String originalTransactionId,
            @Param("currentId") Long currentId);
}
