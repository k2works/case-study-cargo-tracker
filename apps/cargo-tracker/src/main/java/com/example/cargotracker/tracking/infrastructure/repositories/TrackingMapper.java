package com.example.cargotracker.tracking.infrastructure.repositories;

import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;

@Mapper
public interface TrackingMapper {

    void insert(TrackingRecord record);

    TrackingRecord findByTrackingNumber(String trackingNumber);

    TrackingRecord findByBookingId(String bookingId);
}
