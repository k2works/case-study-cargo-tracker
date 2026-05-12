package com.example.bookingms.infrastructure.repositories;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis マッパーインターフェース（見積）
 */
@Mapper
public interface EstimateMapper {

    void insertEstimate(EstimateRecord estimateRecord);

    void insertRouteCandidate(RouteCandidateRecord candidateRecord);

    Optional<EstimateRecord> findByEstimateId(@Param("estimateId") String estimateId);

    List<RouteCandidateRecord> findCandidatesByEstimateDbId(@Param("estimateDbId") Long estimateDbId);
}
