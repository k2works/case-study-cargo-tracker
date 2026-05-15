package com.example.cargotracker.routingms.infrastructure.persistence;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** routing_read_db への MyBatis アクセス。 */
@Mapper
public interface VoyageMapper {

    @Insert("""
            INSERT INTO voyage (
                voyage_number, carrier_code, carrier_name, ship_name,
                departure_date, arrival_date, origin_unlocode, destination_unlocode,
                status, registered_at, updated_at, version
            ) VALUES (
                #{voyageNumber}, #{carrierCode}, #{carrierName}, #{shipName},
                #{departureDate}, #{arrivalDate}, #{originUnlocode}, #{destinationUnlocode},
                #{status}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
            )
            """)
    void insertVoyage(VoyageRecord entity);

    @Insert("""
            INSERT INTO carrier_movement (
                voyage_number, movement_seq, departure_unlocode, arrival_unlocode,
                departure_time, arrival_time
            ) VALUES (
                #{voyageNumber}, #{movementSeq}, #{departureUnlocode}, #{arrivalUnlocode},
                #{departureTime}, #{arrivalTime}
            )
            """)
    void insertCarrierMovement(CarrierMovementRecord entity);

    @Insert("""
            INSERT INTO voyage_accepted_cargo_type (voyage_number, cargo_type)
            VALUES (#{voyageNumber}, #{cargoType})
            """)
    void insertAcceptedCargoType(@Param("voyageNumber") String voyageNumber,
                                 @Param("cargoType") String cargoType);

    @Select("SELECT COUNT(*) > 0 FROM voyage WHERE voyage_number = #{voyageNumber}")
    boolean existsByVoyageNumber(String voyageNumber);

    /** US25: voyage の出発・到着日時を更新する（status は維持、version をインクリメント）。 */
    @Update("""
            UPDATE voyage
               SET departure_date = #{departureDate},
                   arrival_date   = #{arrivalDate},
                   updated_at     = CURRENT_TIMESTAMP,
                   version        = version + 1
             WHERE voyage_number = #{voyageNumber}
            """)
    int updateVoyageSchedule(VoyageRecord entity);

    /** US25: 既存の寄港地を全削除する（再投影前のクリーンアップ）。 */
    @Delete("DELETE FROM carrier_movement WHERE voyage_number = #{voyageNumber}")
    int deleteCarrierMovementsByVoyageNumber(@Param("voyageNumber") String voyageNumber);

    /** US25: 既存の対応貨物種別を全削除する（再投影前のクリーンアップ）。 */
    @Delete("DELETE FROM voyage_accepted_cargo_type WHERE voyage_number = #{voyageNumber}")
    int deleteAcceptedCargoTypesByVoyageNumber(@Param("voyageNumber") String voyageNumber);

    /** US25 / API レスポンス: 単一 Voyage を取得（存在しない場合 null）。 */
    @Select("""
            SELECT voyage_number, carrier_code, carrier_name, ship_name,
                   departure_date, arrival_date, origin_unlocode, destination_unlocode, status
              FROM voyage
             WHERE voyage_number = #{voyageNumber}
            """)
    @Result(property = "voyageNumber", column = "voyage_number")
    @Result(property = "carrierCode", column = "carrier_code")
    @Result(property = "carrierName", column = "carrier_name")
    @Result(property = "shipName", column = "ship_name")
    @Result(property = "departureDate", column = "departure_date")
    @Result(property = "arrivalDate", column = "arrival_date")
    @Result(property = "originUnlocode", column = "origin_unlocode")
    @Result(property = "destinationUnlocode", column = "destination_unlocode")
    @Result(property = "status", column = "status")
    VoyageRecord findByVoyageNumber(@Param("voyageNumber") String voyageNumber);

    @Select("""
            SELECT voyage_number, carrier_code, carrier_name, ship_name,
                   departure_date, arrival_date, origin_unlocode, destination_unlocode, status
            FROM voyage
            ORDER BY departure_date DESC
            """)
    @Result(property = "voyageNumber", column = "voyage_number")
    @Result(property = "carrierCode", column = "carrier_code")
    @Result(property = "carrierName", column = "carrier_name")
    @Result(property = "shipName", column = "ship_name")
    @Result(property = "departureDate", column = "departure_date")
    @Result(property = "arrivalDate", column = "arrival_date")
    @Result(property = "originUnlocode", column = "origin_unlocode")
    @Result(property = "destinationUnlocode", column = "destination_unlocode")
    @Result(property = "status", column = "status")
    List<VoyageRecord> findAll();
}
