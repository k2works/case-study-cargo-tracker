package com.example.simulationms.interfaces.rest;

import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.auth.Role;
import com.example.simulationms.application.internal.commandservices.ContinuousRunScheduler;
import com.example.simulationms.application.internal.queryservices.SimulationStatistics;
import com.example.simulationms.domain.model.aggregates.ContinuousRunSession;
import com.example.simulationms.domain.model.valueobjects.ContinuousRunPolicy;
import com.example.simulationms.domain.model.valueobjects.Seed;
import com.example.simulationms.domain.model.valueobjects.SessionId;
import com.example.simulationms.domain.repository.ContinuousRunSessionRepository;
import com.example.simulationms.domain.repository.SimulationRunRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 継続実行の開始・停止・状態（US37）。
 *
 * <p><strong>システム管理者だけに開く。</strong>業務データを作り続ける操作である。
 *
 * <p><strong>認可は入力の検査より先に置く</strong>（[ADR-016]）——先に走ると、
 * 権限の無い相手に入力仕様を教える。
 */
@RestController
@RequestMapping("/api/v1/simulations/sessions")
public class ContinuousRunController {

    /** 統計を数える範囲。上限が無いと、件数が増えた日に統計が開かなくなる。 */
    /**
     * 一覧に出す過去セッションの数。
     *
     * <p>再現に使うのは<strong>直近の数本</strong>である。無制限に並べても、
     * 昨日より前の種は使われない。
     */
    private static final int RECENT_SESSION_LIMIT = 20;

    private static final int STATISTICS_LIMIT = 500;

    private final ContinuousRunScheduler scheduler;
    private final ContinuousRunSessionRepository sessions;
    private final SimulationRunRepository runs;

    /** 実行してよいか（[ADR-030] 決定 4）。**設定の名前どおりに止める**。 */
    private final boolean enabled;

    /** 見切りの判定に使う時計。**業務の暦で読む**（[ADR-010]）。 */
    private final java.time.Clock clock;

    public ContinuousRunController(ContinuousRunScheduler scheduler,
            ContinuousRunSessionRepository sessions, SimulationRunRepository runs,
            java.time.Clock clock,
            @Value("${app.simulation.enabled:false}") boolean enabled) {
        this.scheduler = scheduler;
        this.sessions = sessions;
        this.runs = runs;
        this.clock = clock;
        this.enabled = enabled;
    }

    @PostMapping
    public ResponseEntity<SessionResponse> start(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @RequestBody StartSessionRequest request) {
        requireAdmin(userId, roles);
        requireEnabled();

        sessions.findActive().ifPresent(active -> {
            // **既に動いていれば断る。**2 つ動くと、どちらの種で何が流れたのかを
            // 追えなくなる——再現の手がかりが消える
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "継続実行が既に動いています: " + active.sessionId().value());
        });

        ContinuousRunSession session = scheduler.start(policyOf(request), seedOf(request), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(SessionResponse.from(session));
    }

    /**
     * 停止する（US37-4）。
     *
     * <p><strong>止めるのは新規の開始だけ</strong>である。進行中の実行は最後まで走るため、
     * 応答は {@code STOPPING} を返しうる——「止めた」と「止まった」は違う。
     */
    @DeleteMapping("/{sessionId}")
    public SessionResponse stop(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable String sessionId) {
        requireAdmin(userId, roles);

        try {
            return SessionResponse.from(scheduler.stop(SessionId.of(sessionId)));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    /** いま動いている継続実行と、その統計（US37-8）。無ければ状態だけを返す。 */
    @GetMapping("/active")
    public ActiveSessionResponse active(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles) {
        requireAdmin(userId, roles);

        SimulationStatistics statistics =
                SimulationStatistics.of(runs.findRecent(STATISTICS_LIMIT),
                        clock.instant().minus(
                                com.example.simulationms.domain.model.aggregates.SimulationRun
                                        .STALE_AFTER));
        return new ActiveSessionResponse(
                sessions.findActive().map(SessionResponse::from).orElse(null),
                StatisticsResponse.from(statistics));
    }

    /**
     * 過去のセッションの一覧（TD-03・IT16）。
     *
     * <p><strong>停止したセッションも残す。</strong>停止した瞬間に種が画面から消えると、
     * 翌朝には落ちた並びを再現する手立てが無い——US37-3 が言う「同じ種を指定すると
     * 同じ並びを再現できる」は、その種を読めて初めて意味を持つ。
     */
    @GetMapping
    public List<SessionResponse> recent(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles) {
        requireAdmin(userId, roles);

        return sessions.findRecent(RECENT_SESSION_LIMIT).stream()
                .map(SessionResponse::from)
                .toList();
    }

    @GetMapping("/{sessionId}")
    public SessionResponse byId(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable String sessionId) {
        requireAdmin(userId, roles);

        return sessions.findById(SessionId.of(sessionId))
                .map(SessionResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "そのセッションはありません: " + sessionId));
    }

    private static ContinuousRunPolicy policyOf(StartSessionRequest request) {
        try {
            // **省略を既定へ落とさない。**落とすと、送り忘れた設定で流れ続ける
            if (request.intervalSeconds() == null || request.maxConcurrent() == null
                    || request.exceptionRatio() == null) {
                throw new IllegalArgumentException(
                        "実行間隔・同時実行数・例外の割合はすべて指定します");
            }
            return ContinuousRunPolicy.of(request.intervalSeconds(), request.maxConcurrent(),
                    request.exceptionRatio());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /** 種を指定しなければ、その場で作る。**作った種は必ず記録される**（US37-3）。 */
    private static Seed seedOf(StartSessionRequest request) {
        return request.seed() == null ? null : Seed.of(request.seed());
    }

    private void requireAdmin(String userId, String roles) {
        if (!AuthenticatedUser.of(userId, roles).hasAnyRole(Role.ROLE_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この操作を行う権限がありません");
        }
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "この環境では業務シミュレーションを実行できません");
        }
    }

    /** 開始の指示。種を指定すると、同じ並びを再現できる（US37-3）。 */
    record StartSessionRequest(Integer intervalSeconds, Integer maxConcurrent,
            BigDecimal exceptionRatio, Long seed) {
    }

    /** 継続実行 1 件。**種を返す**——落ちた実行を再現するために読み取る。 */
    record SessionResponse(String sessionId, long seed, int intervalSeconds, int maxConcurrent,
            BigDecimal exceptionRatio, String status, String statusLabel, String startedBy,
            String startedAt, String stoppedAt) {

        static SessionResponse from(ContinuousRunSession session) {
            return new SessionResponse(session.sessionId().value(), session.seed().value(),
                    session.policy().intervalSeconds(), session.policy().maxConcurrent(),
                    session.policy().exceptionRatio(), session.status().name(),
                    session.status().label(), session.startedBy(),
                    session.startedAt().toString(),
                    session.stoppedAt().map(Object::toString).orElse(null));
        }
    }

    /** 統計（US37-8）。**失敗した工程の分布まで返す**——件数だけでは直す場所が決まらない。 */
    record StatisticsResponse(int total, int succeeded, int failed, int running, int abandoned,
            List<StepFailure> failuresByStep) {

        static StatisticsResponse from(SimulationStatistics statistics) {
            return new StatisticsResponse(statistics.total(), statistics.succeeded(),
                    statistics.failed(), statistics.running(), statistics.abandoned(),
                    statistics.failuresByStep().entrySet().stream()
                            .map(entry -> new StepFailure(entry.getKey().name(),
                                    entry.getKey().label(), entry.getValue()))
                            .toList());
        }
    }

    record StepFailure(String step, String label, int count) {
    }

    /** 動いていなければ {@code session} は null。統計はいつでも読める。 */
    record ActiveSessionResponse(SessionResponse session, StatisticsResponse statistics) {
    }
}
