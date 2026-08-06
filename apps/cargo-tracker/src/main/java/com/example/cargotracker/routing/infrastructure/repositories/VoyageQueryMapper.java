package com.example.cargotracker.routing.infrastructure.repositories;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 航海の読み取り専用マッパー（US07。CQRS のクエリ側）。
 *
 * <p><strong>航海の端点は SQL 側で求める。</strong> 航海ごとに区間を読み直すと、
 * 一覧を開くだけで航海の数だけクエリが飛ぶ（N+1）。
 * 最初の区間の出発地が出発港、最後の区間の到着地が目的港である。
 */
@Mapper
public interface VoyageQueryMapper {

    /**
     * 端点を求める副問い合わせ。
     *
     * <p>{@code seq_number} の最小・最大の行を取ることで、区間の並びから
     * 航海の出発と到着を 1 度の走査で求める。
     */
    String ENDPOINTS = """
            LEFT JOIN LATERAL (
                SELECT cm.departure_location_unlocode AS origin, cm.departure_date
                  FROM carrier_movement cm
                 WHERE cm.voyage_id = v.id
                 ORDER BY cm.seq_number LIMIT 1
            ) first_leg ON TRUE
            LEFT JOIN LATERAL (
                SELECT cm.arrival_location_unlocode AS destination, cm.arrival_date
                  FROM carrier_movement cm
                 WHERE cm.voyage_id = v.id
                 ORDER BY cm.seq_number DESC LIMIT 1
            ) last_leg ON TRUE
            LEFT JOIN location origin_loc ON origin_loc.unlocode = first_leg.origin
            LEFT JOIN location dest_loc ON dest_loc.unlocode = last_leg.destination
            """;

    String CONDITIONS = """
            <where>
              <if test="origin != null and origin != ''">
                AND first_leg.origin = #{origin}
              </if>
              <if test="destination != null and destination != ''">
                AND last_leg.destination = #{destination}
              </if>
              <if test="departureFrom != null">
                AND first_leg.departure_date >= #{departureFrom}
              </if>
              <if test="departureTo != null">
                AND first_leg.departure_date &lt;= #{departureTo}
              </if>
              <!-- 貨物種別はカンマ区切り。前後にカンマを付けて部分一致の誤検出を防ぐ -->
              <if test="cargoType != null and cargoType != ''">
                AND ',' || v.cargo_types || ',' LIKE '%,' || #{cargoType} || ',%'
              </if>
            </where>
            """;

    @Select("""
            <script>
            SELECT v.voyage_number  AS voyageNumber,
                   v.vessel_name    AS vesselName,
                   v.carrier_name   AS carrierName,
                   first_leg.origin AS origin,
                   origin_loc.name  AS originName,
                   last_leg.destination AS destination,
                   dest_loc.name    AS destinationName,
                   first_leg.departure_date AS departureTime,
                   last_leg.arrival_date    AS arrivalTime,
                   (SELECT COUNT(*) FROM carrier_movement cm WHERE cm.voyage_id = v.id)
                       AS movementCount,
                   v.cargo_types    AS cargoTypes
              FROM voyage v
            """ + ENDPOINTS + CONDITIONS + """
            ORDER BY first_leg.departure_date NULLS LAST, v.voyage_number
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<VoyageQueryRow> search(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("departureFrom") Instant departureFrom,
            @Param("departureTo") Instant departureTo,
            @Param("cargoType") String cargoType,
            @Param("offset") int offset,
            @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*) FROM voyage v
            """ + ENDPOINTS + CONDITIONS + """
            </script>
            """)
    long count(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("departureFrom") Instant departureFrom,
            @Param("departureTo") Instant departureTo,
            @Param("cargoType") String cargoType);
}
