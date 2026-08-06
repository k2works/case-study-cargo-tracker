package com.example.cargotracker.shipper.infrastructure.repositories;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ShipperMapper {

    void insert(ShipperRecord shipperRecord);

    ShipperRecord findById(String id);

    ShipperRecord findByEmail(String email);

    ShipperRecord findByCode(String code);

    List<ShipperRecord> findAll();
}
