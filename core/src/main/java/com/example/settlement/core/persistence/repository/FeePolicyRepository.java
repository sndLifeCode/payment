package com.example.settlement.core.persistence.repository;

import com.example.settlement.core.domain.PaymentMethod;
import com.example.settlement.core.persistence.entity.FeePolicy;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeePolicyRepository extends JpaRepository<FeePolicy, Long> {

    @Query(
            """
            select fp from FeePolicy fp
            where fp.pgCompany = :pgCompany
              and fp.merchantId = :merchantId
              and fp.paymentMethod = :paymentMethod
              and fp.startDate <= :transactionDate
              and fp.endDate >= :transactionDate
            order by fp.startDate desc
            """)
    Page<FeePolicy> findActivePolicy(
            @Param("pgCompany") String pgCompany,
            @Param("merchantId") String merchantId,
            @Param("paymentMethod") PaymentMethod paymentMethod,
            @Param("transactionDate") LocalDate transactionDate,
            Pageable pageable);

    @Query(
            """
            select case when count(fp) > 0 then true else false end
            from FeePolicy fp
            where fp.pgCompany = :pgCompany
              and fp.merchantId = :merchantId
              and fp.paymentMethod = :paymentMethod
              and not (fp.endDate < :newStart or fp.startDate > :newEnd)
              and (:excludeId is null or fp.id <> :excludeId)
            """)
    boolean existsOverlappedPolicy(
            @Param("pgCompany") String pgCompany,
            @Param("merchantId") String merchantId,
            @Param("paymentMethod") PaymentMethod paymentMethod,
            @Param("newStart") LocalDate newStart,
            @Param("newEnd") LocalDate newEnd,
            @Param("excludeId") Long excludeId);

    @Query(
            """
            select fp from FeePolicy fp
            where (:pgCompany is null or fp.pgCompany = :pgCompany)
              and (:merchantId is null or fp.merchantId = :merchantId)
              and (:paymentMethod is null or fp.paymentMethod = :paymentMethod)
              and (
                    :targetDate is null
                    or (fp.startDate <= :targetDate and fp.endDate >= :targetDate)
              )
            order by fp.id desc
            """)
    Page<FeePolicy> search(
            @Param("pgCompany") String pgCompany,
            @Param("merchantId") String merchantId,
            @Param("paymentMethod") PaymentMethod paymentMethod,
            @Param("targetDate") LocalDate targetDate,
            Pageable pageable);
}
