package com.example.trackingms.infrastructure.repositories.mybatis;

import com.example.trackingms.domain.projections.TrackingEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 追跡イベント履歴用の MyBatis Mapper（US17 / IT5 2.3）。
 *
 * <p>{@code tracking_event} テーブル（挿入のみ・更新なし）への履歴記録を担当する。
 * SQL は {@code resources/mapper/TrackingEventMapper.xml} で定義する。</p>
 */
@Mapper
public interface TrackingEventMapper {

    /**
     * 追跡イベントを 1 行挿入する（時系列追記のみ）。
     *
     * @param trackingNumber  追跡番号
     * @param occurredAt      事象発生時刻
     * @param eventType       イベント種別（TRACKING_INITIALIZED / STATUS_UPDATED 等）
     * @param transportStatus 関連する輸送状態（任意）
     * @param unlocode        港湾コード（任意）
     * @param voyageNumber    航海番号（任意）
     * @param handlingType    荷役種別（任意。IT5 後半で利用）
     * @param source          記録元（MANUAL / HANDLING / SYSTEM）
     * @param description     任意の説明
     */
    void insertTrackingEvent(@Param("trackingNumber") String trackingNumber,
                             @Param("occurredAt") LocalDateTime occurredAt,
                             @Param("eventType") String eventType,
                             @Param("transportStatus") String transportStatus,
                             @Param("unlocode") String unlocode,
                             @Param("voyageNumber") String voyageNumber,
                             @Param("handlingType") String handlingType,
                             @Param("source") String source,
                             @Param("description") String description);

    /** 追跡番号で履歴を時系列（occurred_at ASC）に取得する。 */
    List<TrackingEvent> findByTrackingNumber(@Param("trackingNumber") String trackingNumber);
}
