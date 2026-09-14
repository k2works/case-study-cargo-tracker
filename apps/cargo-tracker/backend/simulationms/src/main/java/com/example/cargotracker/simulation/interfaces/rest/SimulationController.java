package com.example.cargotracker.simulation.interfaces.rest;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import com.example.cargotracker.simulation.infrastructure.config.SimulationProperties;
import com.example.cargotracker.simulation.infrastructure.persistence.SimulationRunMapper;
import com.example.cargotracker.simulation.infrastructure.query.SimulationQueries;
import com.example.cargotracker.simulation.infrastructure.query.SimulationQueryHandler;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 業務シミュレーションの入口（S92・S93 / US33・US34）。
 *
 * <p><b>管理者だけが使う</b>（Gateway が絞る）。実データに紛れる貨物を作れる操作で、
 * 実行結果には他の利用者の識別子も並ぶ。</p>
 */
@RestController
@RequestMapping("/api/v1/simulation")
public class SimulationController {

    /** 一覧に出す上限。**黙って切らない**——画面が件数を知らせる。 */
    private static final int RECENT_LIMIT = 50;

    private final SimulationQueryHandler queries;
    private final SimulationRunMapper runs;
    private final SimulationProperties properties;

    public SimulationController(SimulationQueryHandler queries, SimulationRunMapper runs,
            SimulationProperties properties) {
        this.queries = queries;
        this.runs = runs;
        this.properties = properties;
    }

    /**
     * シナリオを実行する（US33 §受入基準 1・4・5）。
     *
     * <p><b>本番では断る。</b> 実データに紛れる貨物を作らない。</p>
     *
     * <p><b>二重実行も断る。</b> 断りに<b>実行中の識別子を添える</b>——
     * 「二重に実行できません」だけでは、いまの結果へ行けない。</p>
     */
    @PostMapping("/runs")
    public ResponseEntity<StartedRun> start(
            @RequestHeader(value = "X-Auth-Username", required = false) String username,
            @jakarta.validation.Valid @RequestBody StartRequest request) {
        if (!properties.enabled()) {
            throw new BusinessRuleViolation(
                    "この環境では業務シミュレーションを実行できません"
                            + "（実データに紛れる貨物を作らないため）");
        }
        var scenario = com.example.cargotracker.simulation.domain.model.valueobjects.Scenario
                .of(request.scenario());
        var running = runs.findRunning(scenario.name());
        if (running != null) {
            throw new IllegalTransition("シナリオ「" + scenario.label()
                    + "」は実行中です（実行 " + running.runId() + "）。"
                    + "その結果を開いてください");
        }
        throw new UnsupportedOperationException(
                "実行の開始は T9 の次で配線する（読み口と入口を先に置く）");
    }

    /** 実行の一覧（S92 / US34 §受入基準 4）。 */
    @GetMapping("/runs")
    public ResponseEntity<SimulationQueries.RunListView> recent() {
        return ResponseEntity.ok(queries.findRecentRuns(RECENT_LIMIT));
    }

    /** 実行の詳細（S93 / US34 §受入基準 1・2・5）。 */
    @GetMapping("/runs/{runId}")
    public ResponseEntity<SimulationQueries.RunView> run(@PathVariable String runId) {
        SimulationQueries.RunView view = queries.findRun(runId);
        return view == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(view);
    }

    /** 実行の入力。 */
    public record StartRequest(@NotBlank String scenario) {
    }

    /** 実行を始めた結果。<b>識別子を返す</b>——画面がその結果へ移る。 */
    public record StartedRun(String runId) {
    }
}
