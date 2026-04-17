package com.example.cargotracker.tracking.infrastructure.repositories;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TrackingMapper {

    void insert(TrackingRecord record);

    TrackingRecord findByTrackingNumber(String trackingNumber);

    TrackingRecord findByBookingId(String bookingId);

    void updateStatus(@Param("trackingNumber") String trackingNumber, @Param("cargoStatus") String cargoStatus);

    void insertHandlingEvent(HandlingEventRecord record);
}
