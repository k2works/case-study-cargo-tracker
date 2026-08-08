package com.example.cargotracker.tracking.infrastructure.repositories;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 追跡レコードの MyBatis マッパー。
 *
 * <p><strong>UUID の型ハンドラは明示する。</strong> 実行時は
 * {@code mybatis.type-handlers-package} の設定で解決されるが、設定を読まない道具
 * （JIG の CRUD 解析）からは解決できず、このマッパーだけが読み飛ばされる
 * （IT5 の P5 で実測）。他のマッパー（{@code CargoMapper}）と書き方も揃う。
 */
@Mapper
public interface TrackingMapper {

    @Insert("""
            INSERT INTO tracking_activity (
                tracking_number, booking_id, transport_status, version,
                destination_unlocode, estimated_arrival_date)
            VALUES (
                #{trackingNumber},
                #{bookingId,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler},
                #{transportStatus}, #{version},
                #{destinationUnlocode}, #{estimatedArrivalDate})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TrackingActivityRecord row);

    /**
     * 輸送状態を更新する。楽観的ロック付き（判断 8）。
     *
     * <p><strong>WHERE 句の version が要である。</strong> 外すと、2 か所で同時に
     * 荷役を登録したとき後の保存が黙って前の状態を消す。
     */
    @Update("""
            UPDATE tracking_activity
               SET transport_status = #{transportStatus},
                   destination_unlocode = #{destinationUnlocode},
                   estimated_arrival_date = #{estimatedArrivalDate},
                   version = version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE tracking_number = #{trackingNumber}
               AND version = #{version}
            """)
    int updateStatus(TrackingActivityRecord row);

    @Select("""
            SELECT id, tracking_number, booking_id, transport_status, version,
                   destination_unlocode AS destinationUnlocode,
                   estimated_arrival_date AS estimatedArrivalDate
              FROM tracking_activity
             WHERE tracking_number = #{trackingNumber}
            """)
    TrackingActivityRecord findByTrackingNumber(@Param("trackingNumber") String trackingNumber);

    @Select("""
            SELECT id, tracking_number, booking_id, transport_status, version,
                   destination_unlocode AS destinationUnlocode,
                   estimated_arrival_date AS estimatedArrivalDate
              FROM tracking_activity
             WHERE booking_id = #{bookingId,typeHandler=com.example.cargotracker.shared.infrastructure.persistence.UUIDTypeHandler}
            """)
    TrackingActivityRecord findByBookingId(@Param("bookingId") UUID bookingId);

    /** イベントを入れ替えるため、既存のイベントを削除する。 */
    @Delete("DELETE FROM tracking_handling_event WHERE tracking_id = #{trackingId}")
    int deleteEvents(@Param("trackingId") long trackingId);

    /** イベントをまとめて登録する。**1 件ずつ INSERT しない。** */
    @Insert("""
            <script>
            INSERT INTO tracking_handling_event (
                tracking_id, event_type, event_time, location_unlocode, voyage_number,
                source, recorded_by)
            VALUES
            <foreach item="e" collection="events" separator=",">
              (#{e.trackingId}, #{e.eventType}, #{e.eventTime},
               #{e.locationUnlocode}, #{e.voyageNumber}, #{e.source}, #{e.recordedBy})
            </foreach>
            </script>
            """)
    int insertEvents(@Param("events") List<TrackingEventRecord> events);

    /**
     * イベントを発生日時の古い順で取得する。
     *
     * <p><strong>ORDER BY event_time を外さない。</strong> 順序が崩れると
     * タイムラインが実際の輸送の順序と食い違う。
     */
    @Select("""
            SELECT tracking_id, event_type, event_time, location_unlocode, voyage_number,
                   source, recorded_by AS recordedBy
              FROM tracking_handling_event
             WHERE tracking_id = #{trackingId}
             ORDER BY event_time
            """)
    List<TrackingEventRecord> findEvents(@Param("trackingId") long trackingId);
}
