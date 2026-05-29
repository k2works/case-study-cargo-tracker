package com.example.trackingms.infrastructure.repositories.mybatis;

import com.example.trackingms.domain.projections.TrackingSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 追跡 Read Model 用の MyBatis Mapper（US14 / US17 / IT5 1.4 + 2.3）。
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

    /**
     * 状態更新（TransportStatusUpdatedEvent で呼び出される、US17 / IT5 2.3）。
     * current_status / current_unlocode / current_voyage_number / last_event_at /
     * updated_at / version を一括で更新する。
     */
    void updateStatus(@Param("trackingNumber") String trackingNumber,
                      @Param("currentStatus") String currentStatus,
                      @Param("currentUnlocode") String currentUnlocode,
                      @Param("currentVoyageNumber") String currentVoyageNumber,
                      @Param("lastEventAt") LocalDateTime lastEventAt);

    /**
     * 誤配送フラグの設定（CargoMisroutedEvent で呼び出される、US17 / IT5 2.3）。
     * misrouted = TRUE と last_event_at を更新する。
     */
    void markMisrouted(@Param("trackingNumber") String trackingNumber,
                       @Param("lastEventAt") LocalDateTime lastEventAt);

    TrackingSummary findByTrackingNumber(@Param("trackingNumber") String trackingNumber);

    TrackingSummary findByBookingId(@Param("bookingId") String bookingId);

    /** 追跡管理一覧用のページング取得（US17 / IT5 2.4、updated_at DESC）。 */
    java.util.List<TrackingSummary> findAll(@Param("offset") int offset, @Param("limit") int limit);

    /** 総件数（ページネーション用）。 */
    long count();
}
