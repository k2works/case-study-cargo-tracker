package com.example.cargotracker.routing.infrastructure.repositories;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface VoyageMapper {

    void insertVoyage(VoyageRecord voyageRecord);

    void insertCarrierMovements(@Param("voyageId") Long voyageId,
                                @Param("movements") List<CarrierMovementRecord> movements);

    VoyageRecord findVoyageByVoyageNumber(String voyageNumber);

    List<CarrierMovementRecord> findMovementsByVoyageId(Long voyageId);

    List<VoyageRecord> findVoyagesByRoute(@Param("originUnlocode") String originUnlocode,
                                          @Param("destinationUnlocode") String destinationUnlocode,
                                          @Param("fromDate") LocalDate fromDate,
                                          @Param("arrivalDeadline") LocalDate arrivalDeadline);

    List<VoyageRecord> findAllVoyages();
}
