package com.example.trackingms.infrastructure.repositories;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * TrackingExceptionEvent MyBatis マッパー
 */
@Mapper
public interface TrackingExceptionEventMapper {
    void insertException(TrackingExceptionEventRecord exceptionRecord);

    List<TrackingExceptionEventRecord> findExceptionsByTrackingId(@Param("trackingId") Long trackingId);

    void updateExceptionResponse(TrackingExceptionEventRecord exceptionRecord);
}
