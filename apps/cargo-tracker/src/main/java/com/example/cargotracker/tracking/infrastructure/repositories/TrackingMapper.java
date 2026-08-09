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

    /**
     * 例外を発生の古い順で取得する（US19 / US20）。
     *
     * <p>並べ替えは集約が行う（画面には新しい順で出す）。ここで古い順にするのは、
     * <strong>登録した順と読み戻した順を一致させる</strong>ためである。
     */
    @Select("""
            SELECT id, tracking_id AS trackingId, exception_type AS exceptionType,
                   location_unlocode AS locationUnlocode, occurred_at AS occurredAt,
                   description, escalation_flag AS escalationFlag,
                   status_before AS statusBefore, resolved_at AS resolvedAt,
                   resolution_notes AS resolutionNotes,
                   revised_arrival AS revisedArrival
              FROM tracking_exception_event
             WHERE tracking_id = #{trackingId}
             ORDER BY occurred_at, id
            """)
    List<TrackingExceptionRecord> findExceptions(@Param("trackingId") long trackingId);

    /**
     * 例外を一覧で引く（US19 / US20）。
     *
     * <p><strong>並び順は「未解決が先、発生の新しい順」</strong>（{@code ui_design.md}）。
     * 例外の一覧は「連絡すべき仕事の待ち行列」であり、片づいたものが上に来ると
     * <strong>いま何をすべきかが読めない</strong>。
     *
     * <p>追跡番号と予約 ID を一緒に引く（例外だけでは誰の貨物か分からない）。
     * 荷主名は ACL 経由で別に引く — <strong>Tracking は Booking の表を知らない</strong>。
     */
    @Select("""
            <script>
            SELECT e.id, e.exception_type AS exceptionType,
                   e.location_unlocode AS locationUnlocode, e.occurred_at AS occurredAt,
                   e.description, e.escalation_flag AS escalationFlag,
                   e.status_before AS statusBefore, e.resolved_at AS resolvedAt,
                   e.resolution_notes AS resolutionNotes,
                   e.revised_arrival AS revisedArrival,
                   t.tracking_number AS trackingNumber,
                   CAST(t.booking_id AS VARCHAR) AS bookingId
              FROM tracking_exception_event e
              JOIN tracking_activity t ON t.id = e.tracking_id
            <where>
              <if test="unresolvedOnly">AND e.resolved_at IS NULL</if>
              <if test="escalatedOnly">AND e.escalation_flag = TRUE</if>
            </where>
             ORDER BY CASE WHEN e.resolved_at IS NULL THEN 0 ELSE 1 END,
                      e.occurred_at DESC, e.id DESC
            </script>
            """)
    List<TrackingExceptionListRow> search(
            @Param("unresolvedOnly") boolean unresolvedOnly,
            @Param("escalatedOnly") boolean escalatedOnly);

    /** 例外 1 件を引く（解決画面）。 */
    @Select("""
            SELECT e.id, e.exception_type AS exceptionType,
                   e.location_unlocode AS locationUnlocode, e.occurred_at AS occurredAt,
                   e.description, e.escalation_flag AS escalationFlag,
                   e.status_before AS statusBefore, e.resolved_at AS resolvedAt,
                   e.resolution_notes AS resolutionNotes,
                   e.revised_arrival AS revisedArrival,
                   t.tracking_number AS trackingNumber,
                   CAST(t.booking_id AS VARCHAR) AS bookingId
              FROM tracking_exception_event e
              JOIN tracking_activity t ON t.id = e.tracking_id
             WHERE e.id = #{exceptionId}
            """)
    TrackingExceptionListRow findExceptionById(@Param("exceptionId") long exceptionId);

    /**
     * 同じ貨物の例外をすべて引く（C19）。
     *
     * <p>未解決を先に、発生の新しい順。<strong>一覧と同じ並びにする</strong>
     * （画面ごとに順序が違うと、同じものを見ている確信が持てない）。
     */
    @Select("""
            SELECT e.id, e.exception_type AS exceptionType,
                   e.location_unlocode AS locationUnlocode, e.occurred_at AS occurredAt,
                   e.description, e.escalation_flag AS escalationFlag,
                   e.status_before AS statusBefore, e.resolved_at AS resolvedAt,
                   e.resolution_notes AS resolutionNotes,
                   e.revised_arrival AS revisedArrival,
                   t.tracking_number AS trackingNumber,
                   CAST(t.booking_id AS VARCHAR) AS bookingId
              FROM tracking_exception_event e
              JOIN tracking_activity t ON t.id = e.tracking_id
             WHERE t.tracking_number = #{trackingNumber}
             ORDER BY CASE WHEN e.resolved_at IS NULL THEN 0 ELSE 1 END,
                      e.occurred_at DESC, e.id DESC
            """)
    List<TrackingExceptionListRow> findExceptionsByTrackingNumber(
            @Param("trackingNumber") String trackingNumber);

    /** 未解決の件数（ダッシュボードのカード）。 */
    @Select("""
            <script>
            SELECT COUNT(*) FROM tracking_exception_event
             WHERE resolved_at IS NULL
            <if test="escalatedOnly">AND escalation_flag = TRUE</if>
            </script>
            """)
    int countUnresolved(@Param("escalatedOnly") boolean escalatedOnly);

    /** 新しく起票された例外を登録する。 */
    @Insert("""
            INSERT INTO tracking_exception_event (
                tracking_id, exception_type, location_unlocode, occurred_at,
                description, escalation_flag, status_before)
            VALUES (
                #{trackingId}, #{exceptionType}, #{locationUnlocode}, #{occurredAt},
                #{description}, #{escalationFlag}, #{statusBefore})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertException(TrackingExceptionRecord row);

    /**
     * 解決の記録を書き込む。
     *
     * <p><strong>すでに解決済みの行は更新しない</strong>（{@code resolved_at IS NULL}）。
     * 二重解決は集約も拒むが、<strong>同時に 2 つのリクエストが来たときは
     * どちらも「未解決」を読んでいる</strong>。最後の砦をここに置く。
     */
    @Update("""
            UPDATE tracking_exception_event
               SET resolved_at = #{resolvedAt},
                   resolution_notes = #{resolutionNotes},
                   revised_arrival = #{revisedArrival},
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{id}
               AND resolved_at IS NULL
            """)
    int resolveException(TrackingExceptionRecord row);
}
