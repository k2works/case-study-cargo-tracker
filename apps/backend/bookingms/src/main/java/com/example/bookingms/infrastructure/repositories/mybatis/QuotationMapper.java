package com.example.bookingms.infrastructure.repositories.mybatis;

import com.example.bookingms.domain.projections.QuotationSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface QuotationMapper {

    @SuppressWarnings("java:S107") // MyBatis Mapper は SQL の全カラムをパラメータに必要とするため許容
    void insertQuotation(@Param("quotationId") String quotationId,
                         @Param("shipperId") String shipperId,
                         @Param("originUnlocode") String originUnlocode,
                         @Param("destinationUnlocode") String destinationUnlocode,
                         @Param("arrivalDeadline") LocalDate arrivalDeadline,
                         @Param("cargoType") String cargoType,
                         @Param("weightKg") BigDecimal weightKg,
                         @Param("estimatedAmount") BigDecimal estimatedAmount,
                         @Param("estimatedCurrency") String estimatedCurrency,
                         @Param("validUntil") LocalDate validUntil,
                         @Param("status") String status);

    void insertCandidate(@Param("quotationId") String quotationId,
                         @Param("candidateSeq") int candidateSeq,
                         @Param("estimatedDays") int estimatedDays,
                         @Param("estimatedCost") BigDecimal estimatedCost,
                         @Param("estimatedCurrency") String estimatedCurrency,
                         @Param("itinerarySummary") String itinerarySummary);

    QuotationSummary findById(@Param("quotationId") String quotationId);

    List<QuotationSummary> findAllPaged(@Param("offset") int offset, @Param("limit") int limit);

    long countAll();
}
