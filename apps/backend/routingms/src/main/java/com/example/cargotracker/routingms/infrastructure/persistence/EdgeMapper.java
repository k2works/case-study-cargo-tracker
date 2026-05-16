package com.example.cargotracker.routingms.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * carrier_movement + voyage_accepted_cargo_type の JOIN クエリ（ADR-0011）。
 *
 * <p>voyage の Write Side（{@code CarrierMovement} VO）への直接参照は禁止。
 * Read Side の RDB テーブルのみを参照する。</p>
 */
@Mapper
public interface EdgeMapper {

    /**
     * 全エッジを (movement, cargo_type) の組み合わせで返す。
     * 同一 movement が複数 cargo_type を持つ場合、行が重複する（Java 側で集約）。
     */
    @Select("""
            SELECT
                cm.voyage_number,
                cm.departure_unlocode,
                cm.arrival_unlocode,
                cm.departure_time,
                cm.arrival_time,
                vct.cargo_type
            FROM carrier_movement cm
            JOIN voyage_accepted_cargo_type vct ON cm.voyage_number = vct.voyage_number
            ORDER BY cm.voyage_number, cm.movement_seq
            """)
    @Result(property = "voyageNumber", column = "voyage_number")
    @Result(property = "departureUnlocode", column = "departure_unlocode")
    @Result(property = "arrivalUnlocode", column = "arrival_unlocode")
    @Result(property = "departureTime", column = "departure_time")
    @Result(property = "arrivalTime", column = "arrival_time")
    @Result(property = "cargoType", column = "cargo_type")
    List<EdgeRecord> findAllEdgeRows();
}
