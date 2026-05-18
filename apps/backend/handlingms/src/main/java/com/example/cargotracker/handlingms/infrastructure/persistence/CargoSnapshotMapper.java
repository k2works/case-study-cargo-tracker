package com.example.cargotracker.handlingms.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CargoSnapshotMapper {

    void upsert(CargoSnapshotRecord record);

    CargoSnapshotRecord findByTrackingNumber(@Param("trackingNumber") String trackingNumber);

    CargoSnapshotRecord findByBookingId(@Param("bookingId") String bookingId);

    void updateTrackingNumber(@Param("bookingId") String bookingId,
                              @Param("trackingNumber") String trackingNumber,
                              @Param("bookingStatus") String bookingStatus);
}
