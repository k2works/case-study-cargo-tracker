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
            INSERT INTO voyage (voyage_number, vessel_name, carrier_name, cargo_types, version)
            VALUES (#{voyageNumber}, #{vesselName}, #{carrierName}, #{cargoTypes}, #{version})
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

    @Select("""
            SELECT id, voyage_number, vessel_name, carrier_name, cargo_types, version
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
}
