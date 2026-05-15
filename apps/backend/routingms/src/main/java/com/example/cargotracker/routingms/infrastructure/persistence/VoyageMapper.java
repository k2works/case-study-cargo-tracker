package com.example.cargotracker.routingms.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

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
    void insertVoyage(VoyageRecord record);

    @Insert("""
            INSERT INTO carrier_movement (
                voyage_number, movement_seq, departure_unlocode, arrival_unlocode,
                departure_time, arrival_time
            ) VALUES (
                #{voyageNumber}, #{movementSeq}, #{departureUnlocode}, #{arrivalUnlocode},
                #{departureTime}, #{arrivalTime}
            )
            """)
    void insertCarrierMovement(CarrierMovementRecord record);

    @Insert("""
            INSERT INTO voyage_accepted_cargo_type (voyage_number, cargo_type)
            VALUES (#{voyageNumber}, #{cargoType})
            """)
    void insertAcceptedCargoType(@Param("voyageNumber") String voyageNumber,
                                 @Param("cargoType") String cargoType);

    @Select("SELECT COUNT(*) > 0 FROM voyage WHERE voyage_number = #{voyageNumber}")
    boolean existsByVoyageNumber(String voyageNumber);

    @Select("""
            SELECT voyage_number, carrier_code, carrier_name, ship_name,
                   departure_date, arrival_date, origin_unlocode, destination_unlocode, status
            FROM voyage
            ORDER BY departure_date DESC
            """)
    @Results({
            @Result(property = "voyageNumber", column = "voyage_number"),
            @Result(property = "carrierCode", column = "carrier_code"),
            @Result(property = "carrierName", column = "carrier_name"),
            @Result(property = "shipName", column = "ship_name"),
            @Result(property = "departureDate", column = "departure_date"),
            @Result(property = "arrivalDate", column = "arrival_date"),
            @Result(property = "originUnlocode", column = "origin_unlocode"),
            @Result(property = "destinationUnlocode", column = "destination_unlocode"),
            @Result(property = "status", column = "status")
    })
    List<VoyageRecord> findAll();
}
