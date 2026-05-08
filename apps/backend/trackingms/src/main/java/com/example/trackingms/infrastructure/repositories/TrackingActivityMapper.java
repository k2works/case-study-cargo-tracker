package com.example.trackingms.infrastructure.repositories;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

/**
 * TrackingActivity MyBatis マッパー
 */
@Mapper
public interface TrackingActivityMapper {
    void insert(TrackingActivityRecord activityRecord);

    Optional<TrackingActivityRecord> findByTrackingNumber(@Param("trackingNumber") String trackingNumber);

    Optional<TrackingActivityRecord> findByBookingId(@Param("bookingId") String bookingId);

    void updateStatus(@Param("id") Long id, @Param("transportStatus") String transportStatus);

    void insertEvent(TrackingHandlingEventRecord eventRecord);

    java.util.List<TrackingHandlingEventRecord> findEventsByTrackingId(@Param("trackingId") Long trackingId);
}
