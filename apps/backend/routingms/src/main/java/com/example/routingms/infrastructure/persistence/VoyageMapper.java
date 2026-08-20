package com.example.routingms.infrastructure.persistence;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface VoyageMapper {

    String VOYAGE_COLUMNS = "id, voyage_number, vessel_name, carrier_name, supported_cargo_types";

    /**
     * 検索の絞り込み。
     *
     * <p>条件の有無は動的 SQL で分ける。{@code #{x} IS NULL} と書くと PostgreSQL が
     * パラメータの型を決められず実行時に落ちる（H2 では通るため気づきにくい）。
     *
     * <p>出発地と目的地は<strong>寄港の順序</strong>で絞る。同じ航海に両方の港があることと、
     * その向きに運べることは別である。順序を見ないと逆向きの航海が候補に出る。
     */
    String SEARCH_CONDITIONS = """
            <if test="criteria.originUnLocode != null">
            AND EXISTS (
                SELECT 1 FROM carrier_movement o
                 WHERE o.voyage_id = v.id
                   AND o.departure_location_unlocode = #{criteria.originUnLocode}
                   <if test="criteria.destinationUnLocode != null">
                   AND EXISTS (
                       SELECT 1 FROM carrier_movement d
                        WHERE d.voyage_id = v.id
                          AND d.arrival_location_unlocode = #{criteria.destinationUnLocode}
                          AND d.seq_number >= o.seq_number)
                   </if>
            )
            </if>
            <if test="criteria.originUnLocode == null and criteria.destinationUnLocode != null">
            AND EXISTS (
                SELECT 1 FROM carrier_movement d
                 WHERE d.voyage_id = v.id
                   AND d.arrival_location_unlocode = #{criteria.destinationUnLocode})
            </if>
            <if test="criteria.departureFrom != null">
            AND EXISTS (
                SELECT 1 FROM carrier_movement f
                 WHERE f.voyage_id = v.id AND f.seq_number = 1
                   AND f.departure_date &gt;= #{criteria.departureFrom})
            </if>
            <if test="criteria.departureTo != null">
            AND EXISTS (
                SELECT 1 FROM carrier_movement t
                 WHERE t.voyage_id = v.id AND t.seq_number = 1
                   AND t.departure_date &lt;= #{criteria.departureTo})
            </if>
            <if test="criteria.cargoType != null">
            AND (v.supported_cargo_types = #{criteria.cargoType}
              OR v.supported_cargo_types LIKE CONCAT(#{criteria.cargoType}, ',%')
              OR v.supported_cargo_types LIKE CONCAT('%,', #{criteria.cargoType}, ',%')
              OR v.supported_cargo_types LIKE CONCAT('%,', #{criteria.cargoType}))
            </if>
            """;

    @Select("SELECT " + VOYAGE_COLUMNS + " FROM voyage WHERE voyage_number = #{voyageNumber}")
    @Results(id = "voyageResult", value = {
        @Result(column = "voyage_number", property = "voyageNumber"),
        @Result(column = "vessel_name", property = "vesselName"),
        @Result(column = "carrier_name", property = "carrierName"),
        @Result(column = "supported_cargo_types", property = "supportedCargoTypes")
    })
    VoyageRecord findByVoyageNumber(@Param("voyageNumber") String voyageNumber);

    @Select("""
            <script>
            SELECT v.id, v.voyage_number, v.vessel_name, v.carrier_name, v.supported_cargo_types
              FROM voyage v
             WHERE 1 = 1
            """ + SEARCH_CONDITIONS + """
             -- 出発が早い順。経路設計者は「いつ出るか」で選ぶ
             ORDER BY (SELECT MIN(m.departure_date) FROM carrier_movement m WHERE m.voyage_id = v.id),
                      v.id
             LIMIT #{limit}
            </script>
            """)
    @Results(id = "voyageSearchResult", value = {
        @Result(column = "voyage_number", property = "voyageNumber"),
        @Result(column = "vessel_name", property = "vesselName"),
        @Result(column = "carrier_name", property = "carrierName"),
        @Result(column = "supported_cargo_types", property = "supportedCargoTypes")
    })
    List<VoyageRecord> search(@Param("criteria") Object criteria, @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*) FROM voyage v WHERE 1 = 1
            """ + SEARCH_CONDITIONS + """
            </script>
            """)
    int countMatching(@Param("criteria") Object criteria);

    /**
     * 区間は航海に属する。名称も一緒に返す。
     *
     * <p>UN/LOCODE だけを返して画面側で名前を引くと、地点マスタの複製が
     * サービスをまたいで必要になる（ADR-014 で複製を配ると決めたのは DB の話であり、
     * 画面に対訳表を持たせる理由にはならない）。
     */
    @Select("""
            SELECT m.id, m.voyage_id, m.departure_location_unlocode, dl.name AS departure_location_name,
                   m.arrival_location_unlocode, al.name AS arrival_location_name,
                   m.departure_date, m.arrival_date, m.seq_number
              FROM carrier_movement m
              JOIN location dl ON dl.unlocode = m.departure_location_unlocode
              JOIN location al ON al.unlocode = m.arrival_location_unlocode
             WHERE m.voyage_id = #{voyageId}
             ORDER BY m.seq_number
            """)
    @Results(id = "movementResult", value = {
        @Result(column = "voyage_id", property = "voyageId"),
        @Result(column = "departure_location_unlocode", property = "departureLocationUnlocode"),
        @Result(column = "departure_location_name", property = "departureLocationName"),
        @Result(column = "arrival_location_unlocode", property = "arrivalLocationUnlocode"),
        @Result(column = "arrival_location_name", property = "arrivalLocationName"),
        @Result(column = "departure_date", property = "departureDate"),
        @Result(column = "arrival_date", property = "arrivalDate"),
        @Result(column = "seq_number", property = "seqNumber")
    })
    List<CarrierMovementRecord> findMovements(@Param("voyageId") Long voyageId);

    @Insert("""
            INSERT INTO voyage (voyage_number, vessel_name, carrier_name, supported_cargo_types)
            VALUES (#{voyageNumber}, #{vesselName}, #{carrierName}, #{supportedCargoTypes})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertVoyage(VoyageRecord row);

    @Update("""
            UPDATE voyage
               SET vessel_name = #{vesselName},
                   carrier_name = #{carrierName},
                   supported_cargo_types = #{supportedCargoTypes},
                   updated_at = NOW()
             WHERE id = #{id}
            """)
    void updateVoyage(VoyageRecord row);

    @Insert("""
            INSERT INTO carrier_movement (voyage_id, departure_location_unlocode,
                                          arrival_location_unlocode, departure_date, arrival_date,
                                          seq_number)
            VALUES (#{voyageId}, #{departureLocationUnlocode}, #{arrivalLocationUnlocode},
                    #{departureDate}, #{arrivalDate}, #{seqNumber})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertMovement(CarrierMovementRecord row);

    @Delete("DELETE FROM carrier_movement WHERE voyage_id = #{voyageId}")
    void deleteMovements(@Param("voyageId") Long voyageId);
}
