package com.example.cargotracker.handlingms.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * cargo_status_history テーブルの MyBatis Mapper（US17 暫定）。
 */
@Mapper
public interface CargoStatusHistoryMapper {

    void insert(CargoStatusHistoryRecord record);

    List<CargoStatusHistoryRecord> findByTrackingNumber(@Param("trackingNumber") String trackingNumber);
}
