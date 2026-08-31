package com.example.simulationms.domain.repository;

import com.example.simulationms.domain.model.aggregates.SimulationRun;
import com.example.simulationms.domain.model.valueobjects.RunId;
import java.util.List;
import java.util.Optional;

/** 実行の記録（US34・US35）。 */
public interface SimulationRunRepository {

    /**
     * 実行を作成する。
     *
     * <p><strong>作成と工程の追記を分ける。</strong>「常に INSERT する save」で追記まで
     * 賄うと、最初の工程を記録したときに実行の行が増える。作成しか起きないうちは
     * 表面化せず、最初の追記で壊れる（IT7 の教訓）。
     */
    void create(SimulationRun run);

    /**
     * 工程の結果を 1 件足す。
     *
     * <p><strong>実行そのものは書き換えない。</strong>状態は工程の結果から導ける。
     * 二重に持つと、片方だけ更新された行が生まれる。
     */
    void appendResult(RunId runId, com.example.simulationms.domain.model.valueobjects.StepResult
            result);

    Optional<SimulationRun> findByRunId(RunId runId);

    /** 新しい順の一覧。上限が無いと、件数が増えた日に一覧が開かなくなる。 */
    List<SimulationRun> findRecent(int limit);

    /**
     * そのシナリオが実行中かどうか（US34-5）。
     *
     * <p>二重実行を断る根拠になる。<strong>部分 UNIQUE では H2 が解釈しない</strong>ため
     * （IT12 で実測）、DB 制約ではなくアプリケーション側の検査で守る。
     */
    Optional<SimulationRun> findRunningByScenario(String scenarioId);

    /**
     * その日に始まった実行の数（実行 ID の連番に使う）。
     *
     * <p><strong>日付の範囲ではなく前置きで数える。</strong>範囲検索にすると、境界の
     * 解釈が DB の方言で変わる。
     */
    int countByRunIdPrefix(String prefix);
}
