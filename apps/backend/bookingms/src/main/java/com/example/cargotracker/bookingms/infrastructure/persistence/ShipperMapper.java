package com.example.cargotracker.bookingms.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ShipperMapper {
    void insert(ShipperRecord record);
    ShipperRecord findByEmail(String email);
    List<ShipperRecord> findAll();
}
