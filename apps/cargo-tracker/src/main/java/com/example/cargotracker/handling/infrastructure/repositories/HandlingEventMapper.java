package com.example.cargotracker.handling.infrastructure.repositories;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface HandlingEventMapper {
    void insert(@Param("row") HandlingEventRecord row);
    List<HandlingEventRecord> findByBookingId(@Param("bookingId") UUID bookingId);
}
