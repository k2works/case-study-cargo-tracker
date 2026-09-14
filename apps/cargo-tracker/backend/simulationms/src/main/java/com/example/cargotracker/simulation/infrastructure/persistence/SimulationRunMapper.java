package com.example.cargotracker.simulation.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 実行の記録（US33・US34 / [ADR-0020] 決定 3）。
 *
 * <p><b>投影ではない。</b> Event Sourcing を適用していないので、ここが正である。</p>
 *
 * <p><b>列名を明示して引く。</b> {@code SELECT *} は列の順で組み立てられるので、
 * あとから {@code ALTER TABLE} で足した列は末尾に来る——record の途中に項目を
 * 足すと全部ずれる（IT8 で実測）。</p>
 */
@Mapper
public interface SimulationRunMapper {

    String COLUMNS = "run_id, scenario_id, status, seed, started_at, finished_at, "
            + "started_by, projected_at";

    String STEP_COLUMNS = "run_id, step_no, kind, outcome, elapsed_ms, produced_id, "
            + "failure_status, failure_message, occurred_at";

    /**
     * 実行を始める。
     *
     * <p><b>二重実行はここで断られる。</b> 部分ユニーク
     * （{@code WHERE status = 'RUNNING'}）が同じシナリオの 2 本目を弾く
     * （US33 §受入基準 5）。**アプリケーション層で数えない**——数えてから入れる
     * 形は、2 つの要求が同時に来たときに両方とも通る。</p>
     */
    @Insert("INSERT INTO simulation_run (" + COLUMNS + ") VALUES ("
            + "#{runId}, #{scenarioId}, #{status}, #{seed}, #{startedAt}, #{finishedAt}, "
            + "#{startedBy}, #{projectedAt})")
    int insert(RunRow row);

    /** 実行の状態を書く（終わったとき）。 */
    @Update("UPDATE simulation_run SET status = #{status}, finished_at = #{finishedAt}, "
            + "projected_at = #{projectedAt} WHERE run_id = #{runId}")
    int updateStatus(@Param("runId") String runId,
            @Param("status") String status,
            @Param("finishedAt") Instant finishedAt,
            @Param("projectedAt") Instant projectedAt);

    /** 工程を 1 件書き足す。 */
    @Insert("INSERT INTO simulation_step (" + STEP_COLUMNS + ") VALUES ("
            + "#{runId}, #{stepNo}, #{kind}, #{outcome}, #{elapsedMs}, #{producedId}, "
            + "#{failureStatus}, #{failureMessage}, #{occurredAt})")
    int insertStep(StepRow row);

    /** 実行 1 件。 */
    @Select("SELECT " + COLUMNS + " FROM simulation_run WHERE run_id = #{runId}")
    RunRow find(@Param("runId") String runId);

    /** 実行の一覧（S92 / US34 §受入基準 4）。<b>新しい順</b>。 */
    @Select("SELECT " + COLUMNS + " FROM simulation_run "
            + "ORDER BY started_at DESC, run_id LIMIT #{limit}")
    List<RunRow> findRecent(@Param("limit") int limit);

    /**
     * 走っている同じシナリオ（US33 §受入基準 5 の案内）。
     *
     * <p>断るのは部分ユニークだが、<b>断りに実行中の識別子を添える</b>ために引く
     * ——「二重に実行できません」だけでは、いまの結果へ行けない。</p>
     */
    @Select("SELECT " + COLUMNS + " FROM simulation_run "
            + "WHERE scenario_id = #{scenarioId} AND status = 'RUNNING'")
    RunRow findRunning(@Param("scenarioId") String scenarioId);

    /** その実行の工程。<b>記録した順</b>。 */
    @Select("SELECT " + STEP_COLUMNS + " FROM simulation_step "
            + "WHERE run_id = #{runId} ORDER BY step_no")
    List<StepRow> findSteps(@Param("runId") String runId);

    /** 実行の行。 */
    record RunRow(
            String runId,
            String scenarioId,
            String status,
            Long seed,
            Instant startedAt,
            Instant finishedAt,
            String startedBy,
            Instant projectedAt) {
    }

    /** 工程の行。 */
    record StepRow(
            String runId,
            int stepNo,
            String kind,
            String outcome,
            Long elapsedMs,
            String producedId,
            Integer failureStatus,
            String failureMessage,
            Instant occurredAt) {
    }
}
