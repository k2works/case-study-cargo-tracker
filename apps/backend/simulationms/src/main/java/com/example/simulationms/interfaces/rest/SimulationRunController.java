package com.example.simulationms.interfaces.rest;

import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.auth.Role;
import com.example.simulationms.application.internal.commandservices.RunSimulationUseCase;
import com.example.simulationms.application.internal.commandservices.SimulationAlreadyRunningException;
import com.example.simulationms.domain.model.aggregates.SimulationRun;
import com.example.simulationms.domain.model.valueobjects.BusinessContextKey;
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

    /**
     * 実行してよいか（[ADR-030] 決定 4）。
     *
     * <p><strong>設定の名前どおりに実行を止める。</strong>起動時の検査だけに使っていると、
     * 「安全側に倒したつもり」で {@code false} にした環境が、そのまま実行を受け付ける。
     * 起動で落とすのは<strong>有効にしてはいけない環境</strong>のためで、
     * この検査は<strong>無効にした環境</strong>のためである。
     */
    private final boolean enabled;

    /**
     * 業務タイムゾーン。
     *
     * <p>日付で 1 日を切るために要る。<strong>UTC で切ると、朝の数時間が前日に入る</strong>
     * ——「昨日の失敗」を探しているのに出てこない（IT9 の学び）。
     */
    private final java.time.ZoneId zone;

    public SimulationRunController(RunSimulationUseCase runSimulation,
            SimulationRunRepository runs,
            @org.springframework.beans.factory.annotation.Value("${app.simulation.enabled:false}")
            boolean enabled,
            java.time.Clock clock) {
        this.runSimulation = runSimulation;
        this.runs = runs;
        this.enabled = enabled;
        this.zone = clock.getZone();
    }

    @PostMapping
    public ResponseEntity<RunResponse> start(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @RequestBody StartRunRequest request) {
        requireAdmin(userId, roles);
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "この環境ではシミュレーションを実行できません");
        }

        Scenario scenario = scenarioOf(request.scenarioId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RunResponse.from(runSimulation.run(scenario, userId)));
    }

    /**
     * 実行の一覧（TD-03・IT16）。
     *
     * <p><strong>日付で絞れる。</strong>絞れないと、継続実行を一晩回した翌朝には
     * 昨日の失敗が窓の外に落ちている——落ちたことは統計で分かっても、どれが落ちたのかに
     * 手が届かない。
     *
     * @param date 業務日（{@code yyyy-MM-dd}）。省くと直近から並べる
     */
    @GetMapping
    public List<RunResponse> recent(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String date) {
        requireAdmin(userId, roles);

        if (date == null || date.isBlank()) {
            return runs.findRecent(RECENT_LIMIT).stream().map(RunResponse::from).toList();
        }
        java.time.LocalDate day = parseDate(date);
        // **業務の暦で 1 日を切る。** UTC で切ると、朝の数時間が前日に入る
        java.time.Instant from = day.atStartOfDay(zone).toInstant();
        return runs.findBetween(from, day.plusDays(1).atStartOfDay(zone).toInstant(),
                        RECENT_LIMIT).stream()
                .map(RunResponse::from)
                .toList();
    }

    /**
     * <strong>読めない日付は断る。</strong>黙って「指定なし」に倒すと、打ち間違えた
     * 管理者には直近が返り、絞ったつもりで別の日を見ることになる。
     */
    private static java.time.LocalDate parseDate(String date) {
        try {
            return java.time.LocalDate.parse(date.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "日付は yyyy-MM-dd の形式で指定してください: " + date);
        }
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
        } catch (IllegalArgumentException _) {
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
        return Scenario.findById(scenarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "そのシナリオは実行できません: " + scenarioId));
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

    /**
     * 工程 1 つの結果。
     *
     * <p>{@code identifierKind} は「何番号か」を人へ伝えるための和名である。
     * <strong>画面に対訳表を持たせない</strong>——工程を足したときに画面だけが
     * 古いままになる。現場では管理者が自分で開くのではなく営業に番号を伝えるため、
     * 種別が読めないと伝えられない。
     */
    record StepResponse(String step, String label, String role, String outcome, long elapsedMs,
            String createdIdentifier, String identifierKind, String failureReason,
            String recordedAt) {

        static StepResponse from(StepResult result) {
            return new StepResponse(
                    result.step().name(),
                    result.step().label(),
                    result.step().role(),
                    result.outcome().name(),
                    result.elapsed().toMillis(),
                    result.createdIdentifier(),
                    result.createdIdentifier() == null ? null
                            : BusinessContextKey.labelOf(result.step().producesKey()),
                    result.failureReason(),
                    result.recordedAt() == null ? null : result.recordedAt().toString());
        }
    }
}
