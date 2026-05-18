package com.example.cargotracker.trackingms.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * tracking_summary テーブルへの MyBatis アクセサ。
 *
 * <p>XML マッパー {@code mybatis/mapper/TrackingSummaryMapper.xml} と対応する。</p>
 */
@Mapper
public interface TrackingSummaryMapper {

    void insert(TrackingSummaryRecord summary);

    Optional<TrackingSummaryRecord> findByTrackingNumber(@Param("trackingNumber") String trackingNumber);

    void updateCurrentStatus(
            @Param("trackingNumber") String trackingNumber,
            @Param("currentStatus") String currentStatus,
            @Param("currentUnlocode") String currentUnlocode,
            @Param("currentVoyageNumber") String currentVoyageNumber,
            @Param("misrouted") boolean misrouted,
            @Param("lastEventAt") LocalDateTime lastEventAt);

    void updateDeliveredAt(
            @Param("trackingNumber") String trackingNumber,
            @Param("deliveredAt") LocalDateTime deliveredAt);
}
