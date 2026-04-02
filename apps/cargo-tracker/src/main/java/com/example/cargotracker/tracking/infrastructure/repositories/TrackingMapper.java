package com.example.cargotracker.tracking.infrastructure.repositories;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface TrackingMapper {
    void insert(@Param("trackingNumber") String trackingNumber,
                @Param("bookingId") String bookingId);
    Optional<TrackingRecord> findByTrackingNumber(@Param("trackingNumber") String trackingNumber);
    Optional<TrackingRecord> findByBookingId(@Param("bookingId") String bookingId);
}
