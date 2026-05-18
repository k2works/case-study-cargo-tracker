package com.example.cargotracker.handlingms.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * handling_activity テーブルの MyBatis Mapper。
 *
 * <p>XML マッパー（mybatis/mapper/HandlingActivityMapper.xml）と対応する。</p>
 */
@Mapper
public interface HandlingActivityMapper {

    void insert(HandlingActivityRecord record);

    HandlingActivityRecord findByActivityId(@Param("activityId") String activityId);

    List<HandlingActivityRecord> findByTrackingNumber(@Param("trackingNumber") String trackingNumber);

    List<HandlingActivityRecord> findAll();
}
