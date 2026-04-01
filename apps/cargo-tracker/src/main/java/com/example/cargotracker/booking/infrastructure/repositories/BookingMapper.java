package com.example.cargotracker.booking.infrastructure.repositories;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;
import java.util.UUID;

@Mapper
public interface BookingMapper {

    void insert(@Param("record") BookingRecord record);

    Optional<BookingRecord> findById(@Param("id") UUID id);
}
