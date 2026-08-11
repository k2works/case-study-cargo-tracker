package com.example.cargotracker.estimation.infrastructure.repositories;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 見積の読み取り専用マッパー（CQRS のクエリ側）。
 *
 * <p><strong>最安候補は SQL で 1 回に畳む。</strong> 見積 1 件ごとに候補を引き直すと、
 * 一覧の行数だけ問い合わせが増える（IT16 の T3 / IT17 の R5）。
 */
@Mapper
public interface EstimateQueryMapper {

    @Select("""
            SELECT CAST(e.estimate_id AS VARCHAR) AS estimateId,
                   e.origin_unlocode              AS origin,
                   e.destination_unlocode         AS destination,
                   e.cargo_type                   AS cargoType,
                   e.weight_kg                    AS weightKg,
                   e.arrival_deadline             AS arrivalDeadline,
                   e.status                       AS status,
                   e.created_at                   AS createdAt,
                   MIN(c.estimated_cost_value)    AS cheapestCost
              FROM estimate e
              LEFT JOIN route_candidate c ON c.estimate_id = e.id
             GROUP BY e.id, e.estimate_id, e.origin_unlocode, e.destination_unlocode,
                      e.cargo_type, e.weight_kg, e.arrival_deadline, e.status, e.created_at
             ORDER BY e.created_at DESC
            """)
    List<EstimateQueryRow> findAll();
}
