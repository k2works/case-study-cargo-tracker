package com.example.cargotracker.handlingms.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * claim_verification テーブルの MyBatis Mapper（US16）。
 */
@Mapper
public interface ClaimVerificationMapper {

    void insert(ClaimVerificationRecord record);

    ClaimVerificationRecord findByActivityId(@Param("activityId") String activityId);
}
