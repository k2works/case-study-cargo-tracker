package com.example.cargotracker.booking.infrastructure.repositories;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface BookingMapper {

    void insert(@Param("row") BookingRecord row);

    void update(@Param("row") BookingRecord row);

    Optional<BookingRecord> findById(@Param("id") UUID id);

    List<BookingRecord> findAll();

    void insertLeg(@Param("row") BookingLegRecord row);

    void deleteLegsByBookingId(@Param("bookingId") UUID bookingId);

    List<BookingLegRecord> findLegsByBookingId(@Param("bookingId") UUID bookingId);
}
