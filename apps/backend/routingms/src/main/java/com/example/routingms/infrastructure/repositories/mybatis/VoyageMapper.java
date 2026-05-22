package com.example.routingms.infrastructure.repositories.mybatis;

import com.example.routingms.domain.projections.VoyageProjection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface VoyageMapper {

    @SuppressWarnings("java:S107") // MyBatis Mapper は SQL の全カラムをパラメータに必要とするため許容
    void insertVoyage(@Param("voyageNumber") String voyageNumber,
                      @Param("carrierCode") String carrierCode,
                      @Param("carrierName") String carrierName,
                      @Param("shipName") String shipName,
                      @Param("originUnlocode") String originUnlocode,
                      @Param("destUnlocode") String destUnlocode,
                      @Param("departureDate") LocalDateTime departureDate,
                      @Param("arrivalDate") LocalDateTime arrivalDate);

    void insertCarrierMovement(@Param("voyageNumber") String voyageNumber,
                               @Param("seq") int seq,
                               @Param("departureUnlocode") String departureUnlocode,
                               @Param("arrivalUnlocode") String arrivalUnlocode,
                               @Param("departureTime") LocalDateTime departureTime,
                               @Param("arrivalTime") LocalDateTime arrivalTime);

    void insertAcceptedCargoType(@Param("voyageNumber") String voyageNumber,
                                 @Param("cargoType") String cargoType);

    void updateVoyage(@Param("voyageNumber") String voyageNumber,
                      @Param("departureDate") LocalDateTime departureDate,
                      @Param("arrivalDate") LocalDateTime arrivalDate);

    void deleteCarrierMovements(@Param("voyageNumber") String voyageNumber);

    void deleteAcceptedCargoTypes(@Param("voyageNumber") String voyageNumber);

    VoyageProjection findByVoyageNumber(@Param("voyageNumber") String voyageNumber);

    List<VoyageProjection> findAll();
}
