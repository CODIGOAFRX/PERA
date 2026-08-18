package com.peraerp.finance.currency;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> {
    Optional<ExchangeRate> findByIdAndCompanyId(UUID id, UUID companyId);
    boolean existsByCompanyIdAndBaseCodeAndQuoteCodeAndRateDateAndSourceIgnoreCase(
            UUID companyId, String baseCode, String quoteCode, LocalDate rateDate, String source);

    Optional<ExchangeRate> findFirstByCompanyIdAndBaseCodeAndQuoteCodeAndActiveTrueAndRateDateLessThanEqualOrderByRateDateDesc(
            UUID companyId, String baseCode, String quoteCode, LocalDate rateDate);

    @Query("select r from ExchangeRate r where r.companyId = :companyId " +
            "and (:baseCode is null or r.baseCode = :baseCode) " +
            "and (:quoteCode is null or r.quoteCode = :quoteCode) " +
            "and (:fromDate is null or r.rateDate >= :fromDate) " +
            "and (:toDate is null or r.rateDate <= :toDate) " +
            "and (:active is null or r.active = :active)")
    Page<ExchangeRate> search(@Param("companyId") UUID companyId,
                              @Param("baseCode") String baseCode,
                              @Param("quoteCode") String quoteCode,
                              @Param("fromDate") LocalDate fromDate,
                              @Param("toDate") LocalDate toDate,
                              @Param("active") Boolean active,
                              Pageable pageable);
}
