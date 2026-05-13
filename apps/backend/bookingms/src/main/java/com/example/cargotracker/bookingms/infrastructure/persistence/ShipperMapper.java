package com.example.cargotracker.bookingms.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ShipperMapper {
    void insert(ShipperRecord shipperRow);
    ShipperRecord findByEmail(String email);
    List<ShipperRecord> findAll();

    @Select("SELECT COUNT(*) > 0 FROM shipper WHERE id = #{id}")
    boolean existsById(Long id);
}
