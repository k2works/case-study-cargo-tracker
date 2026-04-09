package com.example.cargotracker.booking.infrastructure.repositories;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CargoMapper {

    void insert(CargoRecord cargoRecord);

    void updateStatus(@Param("bookingId") String bookingId, @Param("bookingStatus") String bookingStatus);

    CargoRecord findByBookingId(String bookingId);

    Long findIdByBookingId(String bookingId);

    List<CargoRecord> findAll();

    List<CargoRecord> findByShipperId(String shipperId);
}
