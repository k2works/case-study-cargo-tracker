package com.example.cargotracker.tracking.infrastructure.repositories;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface TrackingMapper {
    void insert(@Param("trackingNumber") String trackingNumber,
                @Param("bookingId") String bookingId);
    Optional<TrackingRecord> findByTrackingNumber(@Param("trackingNumber") String trackingNumber);
    Optional<TrackingRecord> findByBookingId(@Param("bookingId") String bookingId);

    /**
     * 追跡番号に紐づく荷役イベントを取得する（completion_time 降順）。
     */
    List<HandlingEventRecord> findHandlingEventsByTrackingNumber(@Param("trackingNumber") String trackingNumber);
}
