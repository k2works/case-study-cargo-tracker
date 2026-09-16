package com.example.cargotracker.tracking.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 例外について荷主へ知らせた記録（US19 §受入基準 3）。
 *
 * <p><b>送信基盤はスコープ外</b>（ui_design.md:120）。通知は現行の手作業
 * （電話・メール）で行い、ここに残るのは「いつ・どうやって・何を伝えたか」。
 * 荷主から「聞いていない」と言われたときに突き合わせる材料になる。</p>
 *
 * <p><b>記録と読み口は対で出す。</b> Event Store に積むだけでは誰も読めず、
 * 「記録で満たす」という受入基準の満たし方そのものが成り立たない
 * （IT10 のレビューで実測）。</p>
 */
@Mapper
public interface ExceptionNotificationMapper {

    /** 読み出す列を並べる。<b>{@code SELECT *} にしない</b>（列順で割り当てられる）。 */
    String COLUMNS = "event_id, tracking_number, exception_id, means, summary, "
            + "notified_by, notified_at, projected_at";

    /**
     * 通知の記録を 1 行足す。
     *
     * <p><b>リプレイで増えない。</b> 主キーは元イベントの識別子で、再配送も
     * 読み直しも同じ行に落ちる。</p>
     */
    int insert(ExceptionNotificationRow row);

    /** その例外に、いつ何を伝えたか（S41）。<b>起きた順</b>に返す。 */
    @Select("SELECT " + COLUMNS + " FROM exception_notification "
            + "WHERE exception_id = #{exceptionId} ORDER BY notified_at, event_id")
    List<ExceptionNotificationRow> findByException(@Param("exceptionId") String exceptionId);

    /** その追跡の通知の記録（S41 がまとめて引く）。 */
    @Select("SELECT " + COLUMNS + " FROM exception_notification "
            + "WHERE tracking_number = #{trackingNumber} ORDER BY notified_at, event_id")
    List<ExceptionNotificationRow> findByTracking(@Param("trackingNumber") String trackingNumber);

    /** 通知の記録 1 行。 */
    record ExceptionNotificationRow(
            String eventId,
            String trackingNumber,
            String exceptionId,
            String means,
            String summary,
            // 誰が伝えたか。Gateway を通れば必ず入るが、入らなかったときに
            // 500 で落とすのは違う（画面では「—」と出す）。
            String notifiedBy,
            Instant notifiedAt,
            Instant projectedAt) {
    }
}
