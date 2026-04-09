package com.example.cargotracker.booking.infrastructure.repositories;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LegMapper {

    void insertAll(@Param("cargoId") Long cargoId, @Param("legs") List<LegRecord> legs);

    List<LegRecord> findByCargoId(Long cargoId);
}
