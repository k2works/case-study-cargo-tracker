package com.example.cargotracker.tracking.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 追跡の履歴（US17 §3 / US18）。正典は data-model.md「tracking_event」。 */
@Mapper
public interface TrackingEventMapper {

    /**
     * 履歴を 1 行足す（US17 §3）。
     *
     * <p><b>同じイベントを 2 度読んでも増えない。</b> 主キーは元イベントの識別子で、
     * 再配送・リプレイは同じ行に落ちる。<b>採番しない</b>——採ると読み直すたびに
     * 同じ内容の行が積み上がる（IT2 で実在した欠陥）。</p>
     */
    int insert(TrackingEventRow row);

    /** その追跡の履歴を<b>起きた順</b>に返す。記録した順ではない（後から入れるとずれる）。 */
    @Select("SELECT event_id, tracking_number, event_type, previous_status, new_status, "
            + "location, occurred_at, recorded_by, projected_at FROM tracking_event "
            + "WHERE tracking_number = #{trackingNumber} "
            + "ORDER BY occurred_at, event_id")
    List<TrackingEventRow> findHistory(@Param("trackingNumber") String trackingNumber);

    /** 履歴の 1 行。 */
    record TrackingEventRow(
            String eventId,
            String trackingNumber,
            String eventType,
            String previousStatus,
            String newStatus,
            String location,
            Instant occurredAt,
            String recordedBy,
            Instant projectedAt) {
    }
}
