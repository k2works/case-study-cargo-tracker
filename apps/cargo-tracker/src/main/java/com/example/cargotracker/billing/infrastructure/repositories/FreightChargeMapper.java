package com.example.cargotracker.billing.infrastructure.repositories;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FreightChargeMapper {

    void insert(@Param("row") FreightChargeRecord row);

    void update(@Param("row") FreightChargeRecord row);

    FreightChargeRecord findById(@Param("id") String id);

    List<FreightChargeRecord> findByBookingId(@Param("bookingId") String bookingId);

    List<FreightChargeRecord> findAll();
}
