package com.example.cargotracker.bookingms.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * US01 用 MyBatis Mapper（quotation / quotation_candidate）。
 */
@Mapper
public interface QuotationMapper {

    @Insert("""
            INSERT INTO quotation (
                quotation_id, shipper_id, origin_unlocode, destination_unlocode,
                arrival_deadline, cargo_type, weight_kg,
                estimated_amount, estimated_currency, valid_until, status,
                hazard_imo_class, hazard_un_number, hazard_declaration,
                created_at, updated_at, version
            ) VALUES (
                #{quotationId}, #{shipperId}, #{originUnlocode}, #{destinationUnlocode},
                #{arrivalDeadline}, #{cargoType}, #{weightKg},
                #{estimatedAmount}, #{estimatedCurrency}, #{validUntil}, #{status},
                #{hazardImoClass}, #{hazardUnNumber}, #{hazardDeclaration},
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
            )
            """)
    void insertQuotation(QuotationRecord entity);

    @Insert("""
            INSERT INTO quotation_candidate (
                quotation_id, candidate_seq, estimated_days,
                estimated_cost, estimated_currency, itinerary_summary, voyage_numbers
            ) VALUES (
                #{quotationId}, #{candidateSeq}, #{estimatedDays},
                #{estimatedCost}, #{estimatedCurrency}, #{itinerarySummary}, #{voyageNumbers}
            )
            """)
    void insertCandidate(QuotationCandidateRecord entity);

    @Select("""
            SELECT quotation_id, shipper_id, origin_unlocode, destination_unlocode,
                   arrival_deadline, cargo_type, weight_kg,
                   estimated_amount, estimated_currency, valid_until, status,
                   hazard_imo_class, hazard_un_number, hazard_declaration
              FROM quotation
             WHERE quotation_id = #{quotationId}
            """)
    @Result(property = "quotationId", column = "quotation_id")
    @Result(property = "shipperId", column = "shipper_id")
    @Result(property = "originUnlocode", column = "origin_unlocode")
    @Result(property = "destinationUnlocode", column = "destination_unlocode")
    @Result(property = "arrivalDeadline", column = "arrival_deadline")
    @Result(property = "cargoType", column = "cargo_type")
    @Result(property = "weightKg", column = "weight_kg")
    @Result(property = "estimatedAmount", column = "estimated_amount")
    @Result(property = "estimatedCurrency", column = "estimated_currency")
    @Result(property = "validUntil", column = "valid_until")
    @Result(property = "status", column = "status")
    @Result(property = "hazardImoClass", column = "hazard_imo_class")
    @Result(property = "hazardUnNumber", column = "hazard_un_number")
    @Result(property = "hazardDeclaration", column = "hazard_declaration")
    QuotationRecord findByQuotationId(@Param("quotationId") String quotationId);

    @Select("""
            SELECT quotation_id, candidate_seq, estimated_days,
                   estimated_cost, estimated_currency, itinerary_summary, voyage_numbers
              FROM quotation_candidate
             WHERE quotation_id = #{quotationId}
             ORDER BY candidate_seq
            """)
    @Result(property = "quotationId", column = "quotation_id")
    @Result(property = "candidateSeq", column = "candidate_seq")
    @Result(property = "estimatedDays", column = "estimated_days")
    @Result(property = "estimatedCost", column = "estimated_cost")
    @Result(property = "estimatedCurrency", column = "estimated_currency")
    @Result(property = "itinerarySummary", column = "itinerary_summary")
    @Result(property = "voyageNumbers", column = "voyage_numbers")
    List<QuotationCandidateRecord> findCandidatesByQuotationId(@Param("quotationId") String quotationId);
}
