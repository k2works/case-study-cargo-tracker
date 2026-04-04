package com.example.cargotracker.routing.infrastructure.repositories;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface VoyageMapper {

    List<String> searchVoyageNumbers(
        @Param("originLocode") String originLocode,
        @Param("destinationLocode") String destinationLocode
    );

    List<String> findAllVoyageNumbers();

    Optional<VoyageRecord> findVoyageByNumber(@Param("voyageNumber") String voyageNumber);

    List<VoyageLegRecord> findLegsByVoyageNumber(@Param("voyageNumber") String voyageNumber);
}
