package com.example.cargotracker.estimation.infrastructure.repositories;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface EstimateMapper {

    void insertEstimate(EstimateRecord record);

    void insertRouteCandidates(@Param("estimateId") Long estimateId,
                               @Param("candidates") List<RouteCandidateRecord> candidates);

    void deleteRouteCandidatesByEstimateId(@Param("estimateId") Long estimateId);

    EstimateRecord findEstimateByEstimateId(@Param("estimateId") String estimateId);

    List<RouteCandidateRecord> findCandidatesByEstimateId(@Param("estimateId") Long estimateId);

    List<EstimateRecord> findAllEstimates();
}
