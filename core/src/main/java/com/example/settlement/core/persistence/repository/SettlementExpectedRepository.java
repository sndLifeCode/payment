package com.example.settlement.core.persistence.repository;

import com.example.settlement.core.domain.TransactionType;
import com.example.settlement.core.persistence.entity.SettlementExpected;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SettlementExpectedRepository extends JpaRepository<SettlementExpected, Long> {

    @Transactional
    @Modifying
    void deleteByBaseDateAndPgCompany(LocalDate baseDate, String pgCompany);

    long countByBaseDateAndPgCompany(LocalDate baseDate, String pgCompany);

    @Query(
            """
            select s from SettlementExpected s
            where s.merchantId = :merchantId
              and s.transactionDate between :fromDate and :toDate
              and (:transactionType is null or s.transactionType = :transactionType)
              and (:amount is null or s.amount = :amount)
            order by s.transactionDate desc, s.id desc
            """)
    Page<SettlementExpected> search(
            @Param("merchantId") String merchantId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("transactionType") TransactionType transactionType,
            @Param("amount") BigDecimal amount,
            Pageable pageable);
}
