package com.example.trackingms.infrastructure.repositories.mybatis;

import com.example.trackingms.domain.projections.TrackingSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 追跡 Read Model 用の MyBatis Mapper（US14 / IT5 1.4）。
 *
 * <p>SQL は {@code resources/mapper/TrackingSummaryMapper.xml} で定義する。</p>
 */
@Mapper
public interface TrackingSummaryMapper {

    /**
     * 追跡初期化（TrackingInitializedEvent で呼び出される）。
     * 初期状態は {@code NOT_RECEIVED}・{@code misrouted=false}。
     */
    void insertTrackingSummary(@Param("trackingNumber") String trackingNumber,
                               @Param("bookingId") String bookingId,
                               @Param("currentStatus") String currentStatus);

    TrackingSummary findByTrackingNumber(@Param("trackingNumber") String trackingNumber);

    TrackingSummary findByBookingId(@Param("bookingId") String bookingId);
}
