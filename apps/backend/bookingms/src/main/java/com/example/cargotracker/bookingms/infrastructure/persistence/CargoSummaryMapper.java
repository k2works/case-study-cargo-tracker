package com.example.cargotracker.bookingms.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

/**
 * cargo_summary Read Model のアクセス。
 */
@Mapper
public interface CargoSummaryMapper {

    @Insert("""
            INSERT INTO cargo_summary (
                booking_id, shipper_id, origin_unlocode, destination_unlocode,
                arrival_deadline, cargo_type, weight_kg,
                length_cm, width_cm, height_cm,
                quantity, product_name,
                hazard_imo_class, hazard_un_number, hazard_declaration,
                temperature_min_c, temperature_max_c,
                booking_status, routing_status,
                created_at, updated_at, version
            ) VALUES (
                #{bookingId}, #{shipperId}, #{originUnlocode}, #{destinationUnlocode},
                #{arrivalDeadline}, #{cargoType}, #{weightKg},
                #{lengthCm}, #{widthCm}, #{heightCm},
                #{quantity}, #{productName},
                #{hazardImoClass}, #{hazardUnNumber}, #{hazardDeclaration},
                #{temperatureMinC}, #{temperatureMaxC},
                #{bookingStatus}, #{routingStatus},
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
            )
            """)
    void insert(CargoSummaryRecord entity);

    @Select("""
            SELECT booking_id, shipper_id, tracking_number, origin_unlocode, destination_unlocode,
                   arrival_deadline, cargo_type, weight_kg, length_cm, width_cm, height_cm,
                   quantity, product_name,
                   hazard_imo_class, hazard_un_number, hazard_declaration,
                   temperature_min_c, temperature_max_c,
                   booking_status, routing_status, estimated_amount, estimated_currency,
                   last_event_at, created_at, updated_at, version
            FROM cargo_summary WHERE booking_id = #{bookingId}
            """)
    @Results(id = "cargoSummaryResultMap", value = {
            @Result(property = "bookingId", column = "booking_id"),
            @Result(property = "shipperId", column = "shipper_id"),
            @Result(property = "trackingNumber", column = "tracking_number"),
            @Result(property = "originUnlocode", column = "origin_unlocode"),
            @Result(property = "destinationUnlocode", column = "destination_unlocode"),
            @Result(property = "arrivalDeadline", column = "arrival_deadline"),
            @Result(property = "cargoType", column = "cargo_type"),
            @Result(property = "weightKg", column = "weight_kg"),
            @Result(property = "lengthCm", column = "length_cm"),
            @Result(property = "widthCm", column = "width_cm"),
            @Result(property = "heightCm", column = "height_cm"),
            @Result(property = "quantity", column = "quantity"),
            @Result(property = "productName", column = "product_name"),
            @Result(property = "hazardImoClass", column = "hazard_imo_class"),
            @Result(property = "hazardUnNumber", column = "hazard_un_number"),
            @Result(property = "hazardDeclaration", column = "hazard_declaration"),
            @Result(property = "temperatureMinC", column = "temperature_min_c"),
            @Result(property = "temperatureMaxC", column = "temperature_max_c"),
            @Result(property = "bookingStatus", column = "booking_status"),
            @Result(property = "routingStatus", column = "routing_status"),
            @Result(property = "estimatedAmount", column = "estimated_amount"),
            @Result(property = "estimatedCurrency", column = "estimated_currency"),
            @Result(property = "lastEventAt", column = "last_event_at"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at"),
            @Result(property = "version", column = "version")
    })
    Optional<CargoSummaryRecord> findByBookingId(String bookingId);

    @Select("""
            SELECT booking_id, shipper_id, tracking_number, origin_unlocode, destination_unlocode,
                   arrival_deadline, cargo_type, weight_kg, length_cm, width_cm, height_cm,
                   quantity, product_name,
                   hazard_imo_class, hazard_un_number, hazard_declaration,
                   temperature_min_c, temperature_max_c,
                   booking_status, routing_status, estimated_amount, estimated_currency,
                   last_event_at, created_at, updated_at, version
            FROM cargo_summary
            ORDER BY created_at DESC
            """)
    @ResultMap("cargoSummaryResultMap")
    List<CargoSummaryRecord> findAll();

    @Select("""
            SELECT booking_id, shipper_id, tracking_number, origin_unlocode, destination_unlocode,
                   arrival_deadline, cargo_type, weight_kg, length_cm, width_cm, height_cm,
                   quantity, product_name,
                   hazard_imo_class, hazard_un_number, hazard_declaration,
                   temperature_min_c, temperature_max_c,
                   booking_status, routing_status, estimated_amount, estimated_currency,
                   last_event_at, created_at, updated_at, version
            FROM cargo_summary
            WHERE booking_status = #{bookingStatus}
            ORDER BY created_at DESC
            """)
    @ResultMap("cargoSummaryResultMap")
    List<CargoSummaryRecord> findByBookingStatus(@Param("bookingStatus") String bookingStatus);

    @Update("""
            UPDATE cargo_summary
               SET booking_status = #{bookingStatus},
                   updated_at = CURRENT_TIMESTAMP,
                   version = version + 1
             WHERE booking_id = #{bookingId}
            """)
    int updateBookingStatus(
            @Param("bookingId") String bookingId,
            @Param("bookingStatus") String bookingStatus);

    @Update("""
            UPDATE cargo_summary
               SET tracking_number = #{trackingNumber}
             WHERE booking_id = #{bookingId}
            """)
    int updateTrackingNumber(
            @Param("bookingId") String bookingId,
            @Param("trackingNumber") String trackingNumber);
}
