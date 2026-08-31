package com.example.simulationms.interfaces.rest;

import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.auth.Role;
import com.example.simulationms.application.internal.commandservices.RunSimulationUseCase;
import com.example.simulationms.application.internal.commandservices.SimulationAlreadyRunningException;
import com.example.simulationms.domain.model.aggregates.SimulationRun;
import com.example.simulationms.domain.model.valueobjects.RunId;
import com.example.simulationms.domain.model.valueobjects.Scenario;
import com.example.simulationms.domain.model.valueobjects.StepResult;
import com.example.simulationms.domain.repository.SimulationRunRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * シミュレーションの実行と確認（US34・US35）。
 *
 * <p><strong>システム管理者だけに開く。</strong>業務データを作る操作であり、業務の担当者が
 * 誤って踏める場所には置かない。
 *
 * <p><strong>認可は入力の検査より先に置く</strong>（[ADR-016]）。{@code @Valid} を使わず
 * 本体で検査するのはそのため——先に走ると、権限の無い相手に入力仕様を教える。
 */
@RestController
@RequestMapping("/api/v1/simulations")
public class SimulationRunController {

    /** 一覧の上限。上限が無いと、件数が増えた日に一覧が開かなくなる。 */
    private static final int RECENT_LIMIT = 50;

    private final RunSimulationUseCase runSimulation;
    private final SimulationRunRepository runs;

    public SimulationRunController(RunSimulationUseCase runSimulation,
            SimulationRunRepository runs) {
        this.runSimulation = runSimulation;
        this.runs = runs;
    }

    @PostMapping
    public ResponseEntity<RunResponse> start(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @RequestBody StartRunRequest request) {
        requireAdmin(userId, roles);

        Scenario scenario = scenarioOf(request.scenarioId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RunResponse.from(runSimulation.run(scenario, userId)));
    }

    @GetMapping
    public List<RunResponse> recent(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles) {
        requireAdmin(userId, roles);

        return runs.findRecent(RECENT_LIMIT).stream().map(RunResponse::from).toList();
    }

    @GetMapping("/{runId}")
    public RunResponse detail(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable String runId) {
        requireAdmin(userId, roles);

        return parse(runId)
                .flatMap(runs::findByRunId)
                .map(RunResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "その実行は見つかりません"));
    }

    /**
     * <strong>catch は解析だけを囲む。</strong>
     *
     * <p>読み出しまで広げると、復元の例外が「見つかりません」に化けて原因が残らない。
     */
    private static Optional<RunId> parse(String runId) {
        try {
            return Optional.of(RunId.of(runId));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    /**
     * 知らないシナリオは断る。
     *
     * <p><strong>既定のシナリオへ落とさない。</strong>落とすと、指示したものと違うものが
     * 流れたことに誰も気づけない。
     */
    private static Scenario scenarioOf(String scenarioId) {
        Scenario standard = Scenario.standardTransport();
        if (!standard.id().equals(scenarioId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "そのシナリオは実行できません: " + scenarioId);
        }
        return standard;
    }

    private void requireAdmin(String userId, String roles) {
        if (!AuthenticatedUser.of(userId, roles).hasAnyRole(Role.ROLE_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この操作を行う権限がありません");
        }
    }

    /** 断るだけで終わらせない。実行中の ID を返し、そこへ行けるようにする（US34-5）。 */
    @ExceptionHandler(SimulationAlreadyRunningException.class)
    public ResponseEntity<AlreadyRunningResponse> handleAlreadyRunning(
            SimulationAlreadyRunningException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new AlreadyRunningResponse(e.getMessage(), e.runningRunId().value()));
    }

    record StartRunRequest(String scenarioId) {
    }

    record AlreadyRunningResponse(String message, String runningRunId) {
    }

    /** 実行 1 件。工程の結果をそのまま並べる——US35 が見たいのは工程ごとの記録である。 */
    record RunResponse(String runId, String scenarioId, String status, String startedBy,
            String startedAt, String finishedAt, String failureReason, List<StepResponse> steps) {

        static RunResponse from(SimulationRun run) {
            return new RunResponse(
                    run.runId().value(),
                    run.scenario().id(),
                    run.status().name(),
                    run.startedBy(),
                    run.startedAt().toString(),
                    run.finishedAt().map(Object::toString).orElse(null),
                    run.failureReason().orElse(null),
                    run.results().stream().map(StepResponse::from).toList());
        }
    }

    record StepResponse(String step, String label, String role, String outcome, long elapsedMs,
            String createdIdentifier, String failureReason, String recordedAt) {

        static StepResponse from(StepResult result) {
            return new StepResponse(
                    result.step().name(),
                    result.step().label(),
                    result.step().role(),
                    result.outcome().name(),
                    result.elapsed().toMillis(),
                    result.createdIdentifier(),
                    result.failureReason(),
                    result.recordedAt() == null ? null : result.recordedAt().toString());
        }
    }
}
