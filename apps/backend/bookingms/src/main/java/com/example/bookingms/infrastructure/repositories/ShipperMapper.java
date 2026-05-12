package com.example.bookingms.infrastructure.repositories;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis マッパーインターフェース（荷主）
 */
@Mapper
public interface ShipperMapper {

    void insertShipper(ShipperRecord shipperRecord);

    Optional<ShipperRecord> findByEmail(@Param("email") String email);

    List<ShipperRecord> findAll();
}
