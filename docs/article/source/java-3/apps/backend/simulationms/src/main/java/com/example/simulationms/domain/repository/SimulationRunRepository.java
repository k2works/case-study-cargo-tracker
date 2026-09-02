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
     *
     * <p><strong>種は必ず残す</strong>（US37-3・[ADR-031] 決定 1）。手で押した実行も
     * 例外ではない——列を NULL 可にすると、記録し忘れても行は書けてしまう。
     *
     * @param seed 使った乱数の種。手で押した実行は乱数を使わないので 0
     * @param sessionId 継続実行のセッション。手で押した実行は {@code null}
     */
    void create(SimulationRun run, com.example.simulationms.domain.model.valueobjects.Seed seed,
            com.example.simulationms.domain.model.valueobjects.SessionId sessionId);

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
     * 期間で絞った一覧（TD-03・IT16）。
     *
     * <p><strong>直近 N 件だけでは、落ちた実行へ翌朝辿り着けない。</strong>継続実行を
     * 一晩回すと、昨日の失敗は朝には窓の外に落ちている——落ちたことは統計で分かっても、
     * どれが落ちたのかに手が届かない。
     *
     * @param from この時刻以降（{@code null} なら下限なし）
     * @param to この時刻より前（{@code null} なら上限なし）
     */
    List<SimulationRun> findBetween(java.time.Instant from, java.time.Instant to, int limit);

    /**
     * そのシナリオが実行中かどうか（US34-5）。
     *
     * <p>二重実行を断る根拠になる。<strong>部分 UNIQUE では H2 が解釈しない</strong>ため
     * （IT12 で実測）、DB 制約ではなくアプリケーション側の検査で守る。
     *
     * <p><strong>止まったきりの実行は、実行中とみなさない。</strong>Pod の再起動や配備で
     * 途中終了した行は、放っておくと永久に「実行中」で残る——そのシナリオは二度と
     * 実行できなくなり、復旧手段が DB を手で触ることしか無くなる。
     * {@code staleBefore} より古い記録しか持たない実行は見切る。
     *
     * <p>工程数は<strong>呼ぶ側のシナリオ定義</strong>から取る。過去の実行の行から取ると、
     * 工程を足した日に古い工程数で比較され、まだ動いている実行を「終わった」と読む。
     */
    Optional<SimulationRun> findRunningByScenario(com.example.simulationms.domain.model
            .valueobjects.Scenario scenario, java.time.Instant staleBefore);

    /**
     * その日に始まった実行の数（実行 ID の連番に使う）。
     *
     * <p><strong>日付の範囲ではなく前置きで数える。</strong>範囲検索にすると、境界の
     * 解釈が DB の方言で変わる。
     */
    int countByRunIdPrefix(String prefix);
}
