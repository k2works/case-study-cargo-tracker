package com.example.cargotracker.estimation.infrastructure.repositories;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 見積の書き込み・読み出しマッパー。 */
@Mapper
public interface EstimateMapper {

    /** 見積を追加する。 */
    @Insert("""
            INSERT INTO estimate (
                estimate_id, origin_unlocode, destination_unlocode,
                arrival_deadline, cargo_type, weight_kg, status, version,
                no_candidate_reason)
            VALUES (
                #{estimateId,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler},
                #{origin}, #{destination}, #{arrivalDeadline}, #{cargoType}, #{weightKg},
                'CREATED', 0, #{noCandidateReason})
            """)
    void insert(EstimateRecord row);

    /** 見積番号で 1 件引く。 */
    @Select("""
            SELECT id, CAST(estimate_id AS VARCHAR) AS estimateId,
                   origin_unlocode AS origin, destination_unlocode AS destination,
                   arrival_deadline AS arrivalDeadline, cargo_type AS cargoType,
                   weight_kg AS weightKg, version,
                   no_candidate_reason AS noCandidateReason
              FROM estimate
             WHERE estimate_id = #{estimateId,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler}
            """)
    EstimateRecord findByEstimateId(@Param("estimateId") UUID estimateId);

    /** 候補を追加する。 */
    @Insert("""
            INSERT INTO route_candidate (
                estimate_id, voyage_number, transit_port, transit_days,
                estimated_cost_value, estimated_cost_currency, priority)
            VALUES (#{estimateId}, #{voyageNumber}, #{transitPort}, #{transitDays},
                    #{estimatedCostValue}, #{estimatedCostCurrency}, #{priority})
            """)
    void insertCandidate(RouteCandidateRecord row);

    /**
     * 見積のルート候補（表示順）。
     *
     * <p><strong>まとめて引く。</strong> 1 件ずつ引き直すと候補の数だけ問い合わせが増える。
     */
    @Select("""
            SELECT voyage_number AS voyageNumber, transit_port AS transitPort,
                   transit_days AS transitDays,
                   estimated_cost_value AS estimatedCostValue,
                   estimated_cost_currency AS estimatedCostCurrency,
                   priority
              FROM route_candidate
             WHERE estimate_id = #{estimateId}
             ORDER BY priority
            """)
    List<RouteCandidateRecord> findCandidates(@Param("estimateId") long estimateId);
}
