package com.example.cargotracker.exception.infrastructure.repositories;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CargoExceptionMapper {
    void insert(@Param("row") CargoExceptionRecord row);
    List<CargoExceptionRecord> findByTrackingNumber(@Param("trackingNumber") String trackingNumber);
}
