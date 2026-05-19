package com.example.cargotracker.trackingms.infrastructure.persistence;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * tracking_exception テーブルへの MyBatis アクセサ（US19）。
 *
 * <p>XML マッパー {@code mybatis/mapper/TrackingExceptionMapper.xml} と対応する。</p>
 */
@Mapper
public interface TrackingExceptionMapper {

    void insert(TrackingExceptionRecord record);

    List<TrackingExceptionRecord> findByTrackingNumber(@Param("trackingNumber") String trackingNumber);
}
