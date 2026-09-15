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
     * その稼働がいま走らせている実行の本数。
     *
     * <p><b>数えるのは自分が作った行だけ</b>——手で流した実行（`schedule_id` が
     * NULL）を数えると、上限が実際より早く埋まる。</p>
     */
    @Select("SELECT count(*) FROM simulation_run "
            + "WHERE schedule_id = #{scheduleId} AND status = 'RUNNING'")
    int countRunning(@Param("scheduleId") String scheduleId);

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
