package com.example.cargotracker.trackingms.infrastructure.persistence;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * tracking_event テーブルへの MyBatis アクセサ。
 */
@Mapper
public interface TrackingEventMapper {

    void insert(TrackingEventRecord event);

    /**
     * 追跡番号に紐付くイベントを発生日時降順で取得する。
     */
    List<TrackingEventRecord> findByTrackingNumberOrderByOccurredAtDesc(
            @Param("trackingNumber") String trackingNumber);
}
