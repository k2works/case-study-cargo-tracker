package com.example.simulationms.infrastructure.repositories;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

/** simulation_run と simulation_step_result への SQL。 */
@Mapper
public interface SimulationRunMapper {

    String RUN_COLUMNS = " r.id, r.run_id, r.scenario_id, r.steps, r.status, r.started_by,"
            + " r.started_at, r.finished_at FROM simulation_run r";

    @Insert("INSERT INTO simulation_run (run_id, scenario_id, steps, status, started_by, started_at)"
            + " VALUES (#{runId}, #{scenarioId}, #{steps}, #{status}, #{startedBy}, #{startedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(SimulationRunRecord row);

    @Select("SELECT" + RUN_COLUMNS + " WHERE r.run_id = #{runId}")
    @Results(id = "runResult", value = {
        @Result(column = "id", property = "id"),
        @Result(column = "run_id", property = "runId"),
        @Result(column = "scenario_id", property = "scenarioId"),
        @Result(column = "steps", property = "steps"),
        @Result(column = "status", property = "status"),
        @Result(column = "started_by", property = "startedBy"),
        @Result(column = "started_at", property = "startedAt"),
        @Result(column = "finished_at", property = "finishedAt")
    })
    SimulationRunRecord findByRunId(@Param("runId") String runId);

    @Select("SELECT" + RUN_COLUMNS + " ORDER BY r.id DESC LIMIT #{limit}")
    @ResultMap("runResult")
    List<SimulationRunRecord> findRecent(@Param("limit") int limit);

    /**
     * そのシナリオで実行中のものを引く（US34-5）。
     *
     * <p><strong>状態は工程の結果から導く。</strong>実行の行に持たせて二重管理すると、
     * 片方だけ更新された行が生まれる。ここでは「失敗した工程が無く、かつ記録した工程が
     * シナリオの工程数に満たない」を実行中とみなす——工程数は呼ぶ側が知っている。
     */
    @Select("SELECT" + RUN_COLUMNS
            + " WHERE r.scenario_id = #{scenarioId}"
            + " AND NOT EXISTS (SELECT 1 FROM simulation_step_result s"
            + "   WHERE s.run_id = r.id AND s.outcome = 'FAILED')"
            + " AND (SELECT COUNT(*) FROM simulation_step_result s2 WHERE s2.run_id = r.id)"
            + "   < #{stepCount}"
            // **止まったきりの実行は実行中とみなさない。**開始も追記もされずに古くなった
            // 行を実行中のまま残すと、そのシナリオは二度と実行できなくなる
            + " AND COALESCE((SELECT MAX(s3.recorded_at) FROM simulation_step_result s3"
            + "   WHERE s3.run_id = r.id), r.started_at) >= #{staleBefore}"
            + " ORDER BY r.id DESC LIMIT 1")
    @ResultMap("runResult")
    SimulationRunRecord findRunningByScenario(@Param("scenarioId") String scenarioId,
            @Param("stepCount") int stepCount,
            @Param("staleBefore") java.time.Instant staleBefore);

    /**
     * その日に始まった実行の数（実行 ID の連番に使う）。
     *
     * <p><strong>前置きで数える。</strong>日付の範囲検索にすると、境界の解釈が方言で変わる。
     */
    @Select("SELECT COUNT(*) FROM simulation_run WHERE run_id LIKE #{prefix} || '%'")
    int countByRunIdPrefix(@Param("prefix") String prefix);

    @Insert("INSERT INTO simulation_step_result"
            + " (run_id, step, outcome, elapsed_ms, created_identifier, failure_reason,"
            + "  recorded_at)"
            + " VALUES (#{runId}, #{step}, #{outcome}, #{elapsedMs}, #{createdIdentifier},"
            + "  #{failureReason}, #{recordedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertResult(SimulationStepResultRecord row);

    @Select("SELECT s.id, s.run_id, s.step, s.outcome, s.elapsed_ms, s.created_identifier,"
            + " s.failure_reason, s.recorded_at FROM simulation_step_result s"
            + " WHERE s.run_id = #{runId} ORDER BY s.id")
    @Result(column = "id", property = "id")
    @Result(column = "run_id", property = "runId")
    @Result(column = "step", property = "step")
    @Result(column = "outcome", property = "outcome")
    @Result(column = "elapsed_ms", property = "elapsedMs")
    @Result(column = "created_identifier", property = "createdIdentifier")
    @Result(column = "failure_reason", property = "failureReason")
    @Result(column = "recorded_at", property = "recordedAt")
    List<SimulationStepResultRecord> findResults(@Param("runId") Long runId);
}
