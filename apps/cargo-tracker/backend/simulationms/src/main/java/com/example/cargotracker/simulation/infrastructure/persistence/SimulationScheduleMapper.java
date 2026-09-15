package com.example.cargotracker.simulation.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 継続実行の稼働（US36）。正典は data-model.md「simulation_read_db」。 */
@Mapper
public interface SimulationScheduleMapper {

    /**
     * 読み出す列を並べる。<b>{@code SELECT *} にしない</b>——record への割り当ては
     * 列の順で決まるので、あとから足した列で全部ずれる。
     */
    String COLUMNS = "schedule_id, seed, interval_seconds, max_concurrent, exception_ratio, "
            + "status, started_by, started_at, stopped_at, projected_at";

    /**
     * 稼働を始める。
     *
     * <p><b>二重の稼働はここで断られる</b>（部分ユニーク）。アプリケーション層で
     * 数えない——数えてから入れる形は、2 つの要求が同時に来たときに両方とも通る。</p>
     */
    @Insert("INSERT INTO simulation_schedule (" + COLUMNS + ") VALUES ("
            + "#{scheduleId}, #{seed}, #{intervalSeconds}, #{maxConcurrent}, "
            + "#{exceptionRatio}, #{status}, #{startedBy}, #{startedAt}, #{stoppedAt}, "
            + "#{projectedAt})")
    int insert(ScheduleRow row);

    @Update("UPDATE simulation_schedule SET status = #{status}, stopped_at = #{stoppedAt}, "
            + "projected_at = #{projectedAt} WHERE schedule_id = #{scheduleId}")
    int updateStatus(@Param("scheduleId") String scheduleId,
            @Param("status") String status,
            @Param("stoppedAt") Instant stoppedAt,
            @Param("projectedAt") Instant projectedAt);

    @Select("SELECT " + COLUMNS + " FROM simulation_schedule WHERE schedule_id = #{scheduleId}")
    ScheduleRow find(@Param("scheduleId") String scheduleId);

    /** 動いている稼働（実行中か停止処理中）。<b>1 本だけ</b>。 */
    @Select("SELECT " + COLUMNS + " FROM simulation_schedule WHERE status <> 'STOPPED'")
    ScheduleRow findActive();

    /**
     * いちばん新しい稼働（止まったものも含む）。
     *
     * <p><b>止めた瞬間に読めなくなってはいけない</b>（US36 §受入基準 3・8）。
     * 夜通し流して翌朝に「何件流れて、どの工程で何件落ちたか」を読むのが本来の
     * 使い方で、止めたら件数も失敗工程の分布も<b>乱数の種も</b>消えるなら、
     * 「同じ種を指定すると同じ並びを再現できる」は実務で成立しない
     * （IT17 のレビューで指摘）。</p>
     */
    // **動いているものを先に見る。** 「いちばん新しい」だけで並べると、
    // 動いている稼働より後に止めた古い稼働が前に来うる——画面に出したいのは
    // まず動いているもので、無いときに限って直前の記録である。
    @Select("SELECT " + COLUMNS + " FROM simulation_schedule "
            + "ORDER BY (status <> 'STOPPED') DESC, started_at DESC, schedule_id DESC LIMIT 1")
    ScheduleRow findLatest();

    /**
     * その稼働がいま走らせている実行の本数。
     *
     * <p><b>数えるのは自分が作った行だけ</b>——手で流した実行（`schedule_id` が
     * NULL）を数えると、上限が実際より早く埋まる。</p>
     */
    @Select("SELECT count(*) FROM simulation_run "
            + "WHERE schedule_id = #{scheduleId} AND status = 'RUNNING'")
    int countRunning(@Param("scheduleId") String scheduleId);

    /**
     * その稼働がこれまでに始めた実行の本数。
     *
     * <p><b>乱数の位置はこれで決まる</b>（US36 §受入基準 1・3）。稼働は記憶を
     * 持たない（毎回 DB から組み直す）ので、位置を記憶に頼ると<b>組み直すたびに
     * 乱数が先頭へ戻り、同じ条件を延々と流す</b>（IT17 のレビューで実測）。</p>
     *
     * <p><b>本数から決めれば再現もできる。</b> 同じ種の N 本目は、いつ数え直しても
     * 同じ条件になる。</p>
     */
    @Select("SELECT count(*) FROM simulation_run WHERE schedule_id = #{scheduleId}")
    int countStarted(@Param("scheduleId") String scheduleId);

    /**
     * その稼働が流した実行の内訳（US36 §受入基準 8）。
     *
     * <p><b>明細から数える。</b> 走らせるたびに足し込む形にすると、記入漏れは
     * 赤くならず永久に残る（IT4 の「インデックスの累計は明細から導く」）。</p>
     */
    @Select("SELECT status, count(*) AS count FROM simulation_run "
            + "WHERE schedule_id = #{scheduleId} GROUP BY status ORDER BY status")
    java.util.List<CountRow> countByStatus(@Param("scheduleId") String scheduleId);

    /**
     * 失敗した工程の分布（US36 §受入基準 8）。
     *
     * <p><b>どの工程で止まりやすいかが、いちばん見たい形である。</b> 件数だけ
     * 出しても、次に何を直すかが決まらない。</p>
     */
    @Select("SELECT s.kind AS status, count(*) AS count FROM simulation_step s "
            + "JOIN simulation_run r ON r.run_id = s.run_id "
            + "WHERE r.schedule_id = #{scheduleId} AND s.outcome = 'FAILED' "
            + "GROUP BY s.kind ORDER BY count(*) DESC, s.kind")
    java.util.List<CountRow> countFailedStepsByKind(@Param("scheduleId") String scheduleId);

    /** 数え上げの 1 行。<b>区分と件数の組</b>。 */
    record CountRow(String status, int count) {
    }

    /** 稼働の行。 */
    record ScheduleRow(
            String scheduleId,
            long seed,
            int intervalSeconds,
            int maxConcurrent,
            BigDecimal exceptionRatio,
            String status,
            String startedBy,
            Instant startedAt,
            Instant stoppedAt,
            Instant projectedAt) {
    }
}
