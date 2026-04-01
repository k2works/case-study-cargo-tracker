package com.example.cargotracker.shipper.infrastructure.repositories;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;
import java.util.UUID;

@Mapper
public interface ShipperMapper {

    void insert(@Param("record") ShipperRecord record);

    Optional<ShipperRecord> findById(@Param("id") UUID id);

    Optional<ShipperRecord> findByEmail(@Param("email") String email);
}
