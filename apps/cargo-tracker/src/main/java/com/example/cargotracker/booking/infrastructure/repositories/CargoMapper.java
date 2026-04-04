package com.example.cargotracker.booking.infrastructure.repositories;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CargoMapper {

    void insert(CargoRecord record);

    CargoRecord findByBookingId(String bookingId);

    List<CargoRecord> findAll();

    List<CargoRecord> findByShipperId(String shipperId);
}
