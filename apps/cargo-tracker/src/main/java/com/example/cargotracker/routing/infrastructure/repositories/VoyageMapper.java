package com.example.cargotracker.routing.infrastructure.repositories;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 航海の MyBatis マッパー。 */
@Mapper
public interface VoyageMapper {

    @Insert("""
            INSERT INTO voyage (
                voyage_number, vessel_name, carrier_name, cargo_types,
                capacity_weight_kg, version)
            VALUES (
                #{voyageNumber}, #{vesselName}, #{carrierName}, #{cargoTypes},
                #{capacityWeightKg}, #{version})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(VoyageRecord row);

    /**
     * 運送区間をまとめて登録する。
     *
     * <p><strong>1 件ずつ INSERT しない。</strong> 区間の数だけ往復すると、
     * 寄港地の多い航海ほど登録が遅くなる。
     */
    @Insert("""
            <script>
            INSERT INTO carrier_movement (
                voyage_id, departure_location_unlocode, arrival_location_unlocode,
                departure_date, arrival_date, seq_number)
            VALUES
            <foreach item="m" collection="movements" separator=",">
              (#{m.voyageId}, #{m.departureLocationUnlocode}, #{m.arrivalLocationUnlocode},
               #{m.departureDate}, #{m.arrivalDate}, #{m.seqNumber})
            </foreach>
            </script>
            """)
    int insertMovements(@Param("movements") List<CarrierMovementRecord> movements);

    /**
     * 運航変更を反映する（US25）。楽観的ロック付き。
     *
     * <p><strong>WHERE 句の version が要である。</strong> 2 人が同じ便を同時に
     * 更新したとき、後の更新が黙って前の更新を消す形にしない。
     * 更新件数 0 が「他の更新が先行した」ことを表す。
     */
    @org.apache.ibatis.annotations.Update("""
            UPDATE voyage
               SET vessel_name        = #{vesselName},
                   carrier_name       = #{carrierName},
                   cargo_types        = #{cargoTypes},
                   capacity_weight_kg = #{capacityWeightKg},
                   version            = version + 1,
                   updated_at         = CURRENT_TIMESTAMP
             WHERE voyage_number = #{voyageNumber}
               AND version = #{version}
            """)
    int update(VoyageRecord row);

    /**
     * 運送区間を入れ替えるため、既存の区間を削除する（US25）。
     *
     * <p>区間は順序を持つ並びであり、**1 本ずつ差分更新すると順序が崩れる**。
     * 並びごと入れ替える。
     */
    @org.apache.ibatis.annotations.Delete(
            "DELETE FROM carrier_movement WHERE voyage_id = #{voyageId}")
    int deleteMovements(@Param("voyageId") long voyageId);

    @Select("""
            SELECT id, voyage_number, vessel_name, carrier_name, cargo_types,
                   capacity_weight_kg, version
              FROM voyage WHERE voyage_number = #{voyageNumber}
            """)
    VoyageRecord findByVoyageNumber(@Param("voyageNumber") String voyageNumber);

    /**
     * 運送区間を順序どおりに取得する。
     *
     * <p><strong>ORDER BY seq_number を外さない。</strong> 順序が崩れると
     * 連結制約の検証で「つながっていない」と判定され、**保存できたものが読めなくなる**。
     */
    @Select("""
            SELECT voyage_id, departure_location_unlocode, arrival_location_unlocode,
                   departure_date, arrival_date, seq_number
              FROM carrier_movement WHERE voyage_id = #{voyageId}
             ORDER BY seq_number
            """)
    List<CarrierMovementRecord> findMovements(@Param("voyageId") long voyageId);

    @Select("SELECT COUNT(*) FROM voyage WHERE voyage_number = #{voyageNumber}")
    long countByVoyageNumber(@Param("voyageNumber") String voyageNumber);

    /**
     * 出発地・目的地の<strong>どちらにも寄港する</strong>航海を返す。
     *
     * <p>寄港の判定は区間の出発地・到着地の両方を見る。出発地は「出る港」、
     * 目的地は「着く港」であることまでを SQL で絞り、
     * <strong>順序の判定はドメインが行う</strong>。
     */
    @Select("""
            SELECT DISTINCT v.id, v.voyage_number, v.vessel_name, v.carrier_name,
                   v.cargo_types, v.capacity_weight_kg, v.version
              FROM voyage v
             WHERE EXISTS (SELECT 1 FROM carrier_movement cm
                            WHERE cm.voyage_id = v.id
                              AND cm.departure_location_unlocode = #{origin})
               AND EXISTS (SELECT 1 FROM carrier_movement cm
                            WHERE cm.voyage_id = v.id
                              AND cm.arrival_location_unlocode = #{destination})
             ORDER BY v.voyage_number
            """)
    List<VoyageRecord> findConnecting(
            @Param("origin") String origin, @Param("destination") String destination);

    /**
     * 複数の航海の運送区間をまとめて取得する。
     *
     * <p><strong>航海ごとに引き直さない</strong>（N+1）。並びは航海・区間の順である。
     */
    @Select("""
            <script>
            SELECT voyage_id, departure_location_unlocode, arrival_location_unlocode,
                   departure_date, arrival_date, seq_number
              FROM carrier_movement
             WHERE voyage_id IN
            <foreach item="id" collection="voyageIds" open="(" separator="," close=")">
              #{id}
            </foreach>
             ORDER BY voyage_id, seq_number
            </script>
            """)
    List<CarrierMovementRecord> findMovementsFor(@Param("voyageIds") List<Long> voyageIds);

    /**
     * 航海ごとの割当済み重量（US09）。
     *
     * <p>確定済み（{@code routing_status = 'ROUTED'}）の貨物が、その航海に積む重量の
     * 合計である。<strong>航海ごとに引き直さない</strong>（N+1）。
     *
     * <p>同じ貨物が同じ航海の区間を 2 本使うことがあるため、
     * <strong>区間ではなく貨物単位で数える</strong>。区間ごとに足すと二重に数える。
     */
    @Select("""
            <script>
            SELECT l.voyage_number AS voyageNumber, SUM(c.weight) AS assignedWeight
              FROM (SELECT DISTINCT cargo_id, voyage_number FROM leg) l
              JOIN cargo c ON c.id = l.cargo_id
             WHERE c.routing_status = 'ROUTED'
            <if test="excludeBookingId != null">
               AND c.booking_id &lt;&gt; #{excludeBookingId,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler}
            </if>
               AND l.voyage_number IN
            <foreach item="n" collection="voyageNumbers" open="(" separator="," close=")">
              #{n}
            </foreach>
             GROUP BY l.voyage_number
            </script>
            """)
    List<VoyageLoadRow> findAssignedWeights(
            @Param("voyageNumbers") List<String> voyageNumbers,
            @Param("excludeBookingId") java.util.UUID excludeBookingId);
}
