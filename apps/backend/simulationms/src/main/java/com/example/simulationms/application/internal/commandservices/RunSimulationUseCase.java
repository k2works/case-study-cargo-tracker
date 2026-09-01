package com.example.simulationms.application.internal.commandservices;

import com.example.simulationms.application.internal.outboundservices.acl.BusinessCallFailedException;
import com.example.simulationms.application.internal.outboundservices.acl.BusinessGateway;
import com.example.simulationms.domain.model.aggregates.SimulationRun;
import com.example.simulationms.domain.model.valueobjects.BusinessContextKey;
import com.example.simulationms.domain.model.valueobjects.RunId;
import com.example.simulationms.domain.model.valueobjects.ScenarioRequest;
import com.example.simulationms.domain.model.valueobjects.Seed;
import com.example.simulationms.domain.model.valueobjects.SessionId;
import com.example.simulationms.domain.model.valueobjects.Scenario;
import com.example.simulationms.domain.model.valueobjects.ScenarioStep;
import com.example.simulationms.domain.model.valueobjects.StepResult;
import com.example.simulationms.domain.repository.RunIdAlreadyTakenException;
import com.example.simulationms.domain.repository.SimulationRunRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * シナリオを 1 本流す（US34）。
 *
 * <p><strong>{@code @Transactional} を置かない</strong>（[ADR-030] 決定 5）。工程ごとに
 * 独立して記録し、失敗しても巻き戻さない。まとめて 1 つのトランザクションにすると、
 * 失敗したときに<strong>どこまで進んだかの記録ごと消える</strong>——US35 が見たいものが
 * 失敗したときだけ残らない。IT12 で入金の記録が巻き戻された形の裏返しである。
 */
public class RunSimulationUseCase {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * これより長く音沙汰の無い実行は、実行中とみなさない。
     *
     * <p>Pod の再起動や配備で途中終了した行を実行中のまま残すと、そのシナリオは
     * <strong>二度と実行できなくなる</strong>。1 本の実行は工程ごとに期限（読み取り 10 秒）を
     * 持つため、全 14 工程が詰まっても 3 分に届かない。余裕を見て 15 分とする。
     */
    private static final Duration STALE_AFTER = Duration.ofMinutes(15);

    /**
     * 採番の採り直し上限。
     *
     * <p>同時に始まった数だけ衝突しうるので、US37 の同時実行数の上限より大きく採る。
     * 無制限にしないのは、番号が枯れたとき（同じ日に 9999 件）に回り続けないため。
     */
    private static final int MAX_NUMBERING_ATTEMPTS = 20;

    private final SimulationRunRepository runs;
    private final BusinessGateway business;
    private final Clock clock;

    public RunSimulationUseCase(SimulationRunRepository runs, BusinessGateway business,
            Clock clock) {
        this.runs = runs;
        this.business = business;
        this.clock = clock;
    }

    /**
     * 管理者が手で押した実行（US34）。
     *
     * <p><strong>同じシナリオが実行中なら断る</strong>（US34-5）。押した人が
     * いま何が動いているかを分からなくなるのを防ぐためである。
     */
    public SimulationRun run(Scenario scenario, String startedBy) {
        runs.findRunningByScenario(scenario, clock.instant().minus(STALE_AFTER))
                .ifPresent(running -> {
                    throw new SimulationAlreadyRunningException(running.runId());
                });
        return execute(scenario, startedBy, Seed.of(0L), null, Map.of());
    }

    /**
     * 継続実行が始めた 1 件（US37）。
     *
     * <p><strong>二重実行の拒否は通さない</strong>（[ADR-031] 決定 6）。継続実行は
     * 同じシナリオを何本も並べて動かすため、US34-5 とそもそも噛み合わない。
     * US34-5 が守っているのは「押した人が分からなくなる」ことであり、
     * ここにはその読み手がいない——開始したのはセッションであって、押した人ではない。
     */
    public SimulationRun runForSession(ScenarioRequest request, SessionId sessionId, Seed seed) {
        return execute(request.scenario(), sessionId.value(), seed, sessionId,
                generatedInput(request));
    }

    /**
     * 乱数が選んだ入力を引き継ぎに載せる（US37-1）。
     *
     * <p><strong>載せなければ、生成器は選んでいるのに業務 API は固定値を受け取る。</strong>
     * 生成器だけを見るテストは、届いていなくても緑になる。
     */
    private static Map<String, String> generatedInput(ScenarioRequest request) {
        return Map.of(
                BusinessContextKey.ORIGIN, request.origin(),
                BusinessContextKey.DESTINATION, request.destination(),
                BusinessContextKey.CARGO_TYPE, request.cargoType(),
                BusinessContextKey.WEIGHT_KG, String.valueOf(request.weightKg()),
                BusinessContextKey.DEADLINE_DAYS, String.valueOf(request.deadlineDays()));
    }

    private SimulationRun execute(Scenario scenario, String startedBy, Seed seed,
            SessionId sessionId, Map<String, String> generatedInput) {
        SimulationRun run = startWithNextFreeRunId(scenario, startedBy, seed, sessionId);
        RunId runId = run.runId();

        Map<String, String> context = new HashMap<>(generatedInput);
        context.put(BusinessContextKey.RUN_ID, runId.value());

        for (ScenarioStep step : scenario.steps()) {
            StepResult result = execute(step, context);
            run = run.withResult(result);
            runs.appendResult(runId, result);

            if (result.failed()) {
                // **後ろの工程は踏まない。**前提の無い操作が次々に失敗すると、
                // 本当に壊れている 1 か所が失敗の列に埋もれる
                break;
            }
            result.identifier()
                    .filter(identifier -> step.produces())
                    .ifPresent(identifier -> context.put(step.producesKey(), identifier));
        }
        return run;
    }

    private StepResult execute(ScenarioStep step, Map<String, String> context) {
        Instant startedAt = clock.instant();
        long startedNanos = System.nanoTime();
        try {
            String identifier = business.execute(step, Map.copyOf(context));
            return StepResult.succeeded(step, elapsedSince(startedNanos),
                    identifier == null || identifier.isBlank() ? null : identifier, startedAt);
        } catch (BusinessCallFailedException e) {
            // **理由を残す。**「失敗しました」だけでは、経路候補が 0 件なのか
            // 接続先が違うのかを切り分けられない
            return StepResult.failed(step, elapsedSince(startedNanos), e.getMessage(), startedAt);
        } catch (RuntimeException e) {
            // **想定していない失敗も、工程の結果として記録する。**
            //
            // 記録せずに抜けると、その実行は工程を 1 つも終えないまま「実行中」で残り、
            // 二重実行の拒否が永久に効いて**誰もそのシナリオを実行できなくなる**。
            // しかも US35 が見たい「どこまで進んだか」が、失敗したときだけ残らない
            // ——`@Transactional` を置かない理由として挙げたのと同じ結果になる。
            //
            // 実際に届く例: 名簿に無いロール（IllegalStateException）、
            // 引き継いだ識別子が数値でない（NumberFormatException）
            return StepResult.failed(step, elapsedSince(startedNanos),
                    "想定していない失敗です（" + e.getClass().getSimpleName() + ": "
                            + e.getMessage() + "）", startedAt);
        }
    }

    private static Duration elapsedSince(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos);
    }

    /**
     * まだ使われていない番号で実行を始める。
     *
     * <p><strong>数える側では衝突を防げない。</strong>「今日の件数 + 1」は、数えてから
     * 書くまでの間に別の実行が入り込む。数え直しても同じ隙間が残る——US37 の継続実行は
     * 同時に複数を始めるため、この隙間は必ず踏む。
     *
     * <p>そこで<strong>一意制約を裁定者にする</strong>。実際に書いてみて、断られたら
     * 次の番号を採る。番号を決める場所と、番号が空いていることを保証する場所を
     * 同じにしない限り、この形以外に隙間は塞げない。
     */
    private SimulationRun startWithNextFreeRunId(Scenario scenario, String startedBy,
            Seed seed, SessionId sessionId) {
        for (int attempt = 1; attempt <= MAX_NUMBERING_ATTEMPTS; attempt++) {
            SimulationRun run = SimulationRun.start(
                    nextRunId(), scenario, startedBy, clock.instant());
            try {
                runs.create(run, seed, sessionId);
                return run;
            } catch (RunIdAlreadyTakenException _) {
                // 別の実行が先に採った。次の番号で採り直す。
            }
        }
        // ここに来るのは、同時開始が上限を超えて続いた場合だけである。
        // 黙って握りつぶすと、実行が始まらない理由が誰にも見えなくなる。
        throw new IllegalStateException(
                "実行 ID の採番が " + MAX_NUMBERING_ATTEMPTS + " 回続けて衝突しました。"
                        + "同時に始めている実行が多すぎます");
    }

    /**
     * その日の連番を採る。
     *
     * <p><strong>前置きで数える。</strong>日付をまたぐ範囲検索にすると、境界の解釈が
     * DB の方言で変わる（IT12 で実測）。
     *
     * <p>ここで得た番号は<strong>空いていることの保証ではない</strong>。
     * 保証するのは {@link #startWithNextFreeRunId} である。
     */
    private RunId nextRunId() {
        String prefix = "SIM-" + LocalDate.now(clock).format(DAY) + "-";
        return RunId.of(prefix + "%04d".formatted(runs.countByRunIdPrefix(prefix) + 1));
    }
}
