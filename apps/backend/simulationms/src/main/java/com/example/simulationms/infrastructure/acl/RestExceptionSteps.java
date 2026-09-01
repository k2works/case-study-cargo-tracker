package com.example.simulationms.infrastructure.acl;

import static com.example.simulationms.infrastructure.acl.BusinessCalls.bearer;
import static com.example.simulationms.infrastructure.acl.BusinessCalls.call;
import static com.example.simulationms.infrastructure.acl.BusinessCalls.required;

import com.example.simulationms.application.internal.outboundservices.acl.BusinessCallFailedException;
import com.example.simulationms.domain.model.valueobjects.BusinessContextKey;
import com.example.simulationms.domain.model.valueobjects.ScenarioStep;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 例外を起こし、対応する工程の出口（US36・[ADR-031] 決定 5）。
 *
 * <p><strong>例外専用の API は 1 つも呼ばない。</strong>誤配は「予定と違う港での荷役を
 * 記録する」、遅延は「予定より遅い日時で記録する」——実際に起きる操作をそのまま行う。
 * 専用の入口を作ると [ADR-026]（誤配は荷役の記録から検知する）の検知を通らない経路が
 * 生まれ、<strong>実際には動かない実装が緑になる</strong>。
 *
 * <p>{@link RestBusinessGateway} から分けたのは行数の都合ではなく、
 * <strong>変わる理由が違う</strong>ためである。正常系は業務の順序が変われば変わり、
 * ここは「例外がどう起きるか」が変われば変わる。
 */
class RestExceptionSteps {

    /** 予定よりどれだけ遅れて荷役を記録するか。遅延として扱われるだけの幅を採る。 */
    private static final int LATE_DAYS = 10;

    /** 予定と違う港。誤配はここでの荷降しから検知される。 */
    private static final String WRONG_PORT = "SGSIN";

    /** 組み直し用の航海の到着まで。期限に十分間に合う位置に置く。 */
    private static final int RECOVERY_ARRIVAL_DAYS = 20;

    /** 到着期限までの日数。組み直しの候補が 0 件にならない幅を採る。 */
    private static final int DEADLINE_DAYS = 120;

    /** 遅延の例外。解決には新しい到着予定日が要る（US19-4）。 */
    private static final String DELAY = "DELAY";

    /** 遅延を解決するときに入れる新しい到着予定日までの日数。 */
    private static final int NEW_ARRIVAL_DAYS = 30;

    private final RestClient gateway;
    private final Clock clock;

    RestExceptionSteps(RestClient gateway, Clock clock) {
        this.gateway = gateway;
        this.clock = clock;
    }

    String execute(ScenarioStep step, String token, Map<String, String> context) {
        return switch (step) {
            case RECORD_LATE_HANDLING -> recordLateHandling(token, context);
            case RAISE_DAMAGE -> raiseException(token, context, "DAMAGE",
                    "シミュレーション：荷降し時に外装の破損を確認");
            case RECORD_MISROUTED_HANDLING -> recordMisroutedHandling(token, context);
            case HOLD_CUSTOMS -> changeCustomsStatus(step, token, context, "HELD",
                    "シミュレーション：追加書類の確認待ち");
            case REQUEST_CANCELLATION -> requestCancellation(token, context);
            case RESOLVE_EXCEPTION -> resolveException(token, context);
            case REGISTER_RECOVERY_VOYAGE -> registerRecoveryVoyage(token, context);
            case REDESIGN_ROUTE -> redesignRoute(token, context);
            case RELEASE_CUSTOMS -> changeCustomsStatus(step, token, context, "PENDING",
                    "シミュレーション：書類の確認完了");
            case APPROVE_CANCELLATION -> approveCancellation(token, context);
            default -> throw new IllegalStateException(
                    "例外の工程ではありません: " + step);
        };
    }

    /**
     * 予定より遅い日時で荷役を記録する。
     *
     * <p>受け取りと積込までは予定どおりに置き、<strong>荷降しだけを遅らせる</strong>。
     * 全部を遅らせると、遅れているのか単に日付がずれているのかを区別できない。
     */
    private String recordLateHandling(String token, Map<String, String> context) {
        String trackingNumber = required(context, BusinessContextKey.TRACKING_NUMBER);
        String voyageNumber = required(context, BusinessContextKey.VOYAGE_NUMBER);
        Instant at = clock.instant();

        recordActivity(ScenarioStep.RECORD_LATE_HANDLING, token, trackingNumber,
                "RECEIVE", RestBusinessGateway.ORIGIN, null, at);
        recordActivity(ScenarioStep.RECORD_LATE_HANDLING, token, trackingNumber,
                "LOAD", RestBusinessGateway.ORIGIN, voyageNumber, at.plus(1, ChronoUnit.HOURS));
        recordActivity(ScenarioStep.RECORD_LATE_HANDLING, token, trackingNumber,
                "UNLOAD", RestBusinessGateway.DESTINATION, voyageNumber,
                at.plus(LATE_DAYS, ChronoUnit.DAYS));

        // 遅れは記録から読み取れるが、対応するには例外として起票されている必要がある。
        return raiseException(token, context, DELAY,
                "シミュレーション：荷降しが予定より " + LATE_DAYS + " 日遅れ");
    }

    /**
     * 予定と違う港で荷降しを記録する（[ADR-026]）。
     *
     * <p>誤配の起票はこちらでは行わない。<strong>記録そのものから検知される</strong>のが
     * 決定であり、ここで起票すると検知が働いていなくても緑になる。
     */
    private String recordMisroutedHandling(String token, Map<String, String> context) {
        String trackingNumber = required(context, BusinessContextKey.TRACKING_NUMBER);
        String voyageNumber = required(context, BusinessContextKey.VOYAGE_NUMBER);
        Instant at = clock.instant();

        recordActivity(ScenarioStep.RECORD_MISROUTED_HANDLING, token, trackingNumber,
                "RECEIVE", RestBusinessGateway.ORIGIN, null, at);
        recordActivity(ScenarioStep.RECORD_MISROUTED_HANDLING, token, trackingNumber,
                "LOAD", RestBusinessGateway.ORIGIN, voyageNumber, at.plus(1, ChronoUnit.HOURS));
        recordActivity(ScenarioStep.RECORD_MISROUTED_HANDLING, token, trackingNumber,
                "UNLOAD", WRONG_PORT, voyageNumber, at.plus(2, ChronoUnit.HOURS));
        return BusinessContextKey.NONE;
    }

    private void recordActivity(ScenarioStep step, String token, String trackingNumber,
            String type, String location, String voyageNumber, Instant at) {
        call(step, () -> gateway.post()
                .uri(RestBusinessGateway.HANDLING_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .body(new BusinessMessages.HandlingActivityRequest(trackingNumber, type,
                        location, at.toString(), RestBusinessGateway.OPERATOR, voyageNumber, null))
                .retrieve()
                .toBodilessEntity());
    }

    /** 気づいた人が例外を起票する（US20-1）。 */
    private String raiseException(String token, Map<String, String> context,
            String type, String description) {
        call(ScenarioStep.RAISE_DAMAGE, () -> gateway.post()
                .uri(RestBusinessGateway.TRACKING_MANAGE_PATH + "/exceptions")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .body(new BusinessMessages.RaiseExceptionRequest(
                        required(context, BusinessContextKey.TRACKING_NUMBER), type, description))
                .retrieve()
                .toBodilessEntity());
        return BusinessContextKey.NONE;
    }

    /**
     * 起きている例外を解決する（US19-4）。
     *
     * <p><strong>解決する相手を読んでから解決する。</strong>番号を推測すると、
     * 別の貨物の例外を閉じうる——閉じた側は誰にも気づかれない。
     */
    private String resolveException(String token, Map<String, String> context) {
        String trackingNumber = required(context, BusinessContextKey.TRACKING_NUMBER);
        BusinessMessages.ManagedTrackingResponse tracking = call(ScenarioStep.RESOLVE_EXCEPTION,
                () -> gateway.get()
                        .uri(RestBusinessGateway.TRACKING_MANAGE_PATH + "/" + trackingNumber)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .retrieve()
                        .body(BusinessMessages.ManagedTrackingResponse.class));

        if (tracking == null || tracking.activeException() == null
                || tracking.activeException().id() == null) {
            throw new BusinessCallFailedException(
                    "解決すべき例外が起きていません（" + trackingNumber + "）。"
                            + "例外を起こす工程が実際には起こしていない");
        }
        Long exceptionId = tracking.activeException().id();

        // **遅延の解決には新しい到着予定日が要る**（US19-4）。添えないと集約が断る
        // ——断られること自体は正しい振る舞いであり、こちらの入力が足りていない。
        // 実環境で実際に踏んだ（400: 遅延を解決するときは、新しい到着予定日を入れてください）。
        String newArrival = DELAY.equals(tracking.activeException().exceptionType())
                ? LocalDate.now(clock).plusDays(NEW_ARRIVAL_DAYS).toString()
                : null;

        call(ScenarioStep.RESOLVE_EXCEPTION, () -> gateway.post()
                .uri(RestBusinessGateway.TRACKING_MANAGE_PATH + "/exceptions/"
                        + exceptionId + "/resolve")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .body(new BusinessMessages.ResolveExceptionRequest(trackingNumber, exceptionId,
                        "シミュレーション：対応完了", newArrival))
                .retrieve()
                .toBodilessEntity());
        return BusinessContextKey.NONE;
    }

    /**
     * 組み直し用の航海番号。
     *
     * <p><strong>20 文字に収める。</strong>{@code voyage_number} は
     * {@code VARCHAR(20)} である（[data-model]）——長い名前を送ると
     * 「value too long」で 500 になる。実環境で実際に踏んだ。
     */
    static String recoveryVoyageNumberOf(String runId) {
        return "VR-" + runId.replace("SIM-", "");
    }

    /**
     * 誤配した港から目的地へ向かう航海を登録する（US36-3）。
     *
     * <p><strong>元の航海では組み直せない。</strong>誤配した港からの区間を持たないため、
     * そのまま割り当てようとすると 409（選んだ経路はもう使えません）で断られる
     * ——実環境で実際に踏んだ。経路設計者が現在地からの航海を探し、無ければ登録する
     * のが実業務の手順である。
     */
    private String registerRecoveryVoyage(String token, Map<String, String> context) {
        String number = recoveryVoyageNumberOf(BusinessCalls.runId(context));
        Instant at = clock.instant();

        call(ScenarioStep.REGISTER_RECOVERY_VOYAGE, () -> gateway.post()
                .uri(RestBusinessGateway.VOYAGE_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .body(new BusinessMessages.VoyageRequest(number, "シミュレーション復旧丸",
                        "シミュレーション海運",
                        java.util.List.of(RestBusinessGateway.CARGO_TYPE),
                        java.util.List.of(new BusinessMessages.VoyageRequest.MovementRequest(
                                WRONG_PORT, RestBusinessGateway.DESTINATION,
                                at.plus(1, ChronoUnit.DAYS),
                                at.plus(RECOVERY_ARRIVAL_DAYS, ChronoUnit.DAYS)))))
                .retrieve()
                .toBodilessEntity());
        return number;
    }

    /**
     * 現在地から経路を組み直す（US36-3）。
     *
     * <p><strong>出発地は現在地である。</strong>元の出発地から引き直すと、
     * すでに運ばれた区間をもう一度運ぶ経路になる——輸送は再開しない。
     *
     * <p><strong>レグは自分で組み立てない。</strong>組み立てると航海のスケジュールと
     * 食い違い、409 で断られる（実環境で実際に踏んだ）。経路候補を引き、
     * <strong>組み直し用の航海を通る候補</strong>を選ぶ——実際の経路設計者と同じ手順である。
     */
    private String redesignRoute(String token, Map<String, String> context) {
        String bookingId = required(context, BusinessContextKey.BOOKING_ID);
        String recoveryVoyage = required(context, BusinessContextKey.RECOVERY_VOYAGE_NUMBER);
        String deadline = LocalDate.now(clock).plusDays(DEADLINE_DAYS).toString();

        BusinessMessages.RouteCandidateListResponse candidates =
                call(ScenarioStep.REDESIGN_ROUTE, () -> gateway.get()
                        .uri(UriComponentsBuilder.fromPath(RestBusinessGateway.ROUTE_PATH)
                                .queryParam("origin", WRONG_PORT)
                                .queryParam("destination", RestBusinessGateway.DESTINATION)
                                .queryParam("deadline", deadline)
                                .queryParam("cargoType", RestBusinessGateway.CARGO_TYPE)
                                .toUriString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .retrieve()
                        .body(BusinessMessages.RouteCandidateListResponse.class));

        if (candidates == null || candidates.candidates() == null) {
            throw new BusinessCallFailedException(
                    "組み直しの経路候補が読めません（" + WRONG_PORT + " → "
                            + RestBusinessGateway.DESTINATION + "）");
        }
        BusinessMessages.RouteCandidateListResponse.Candidate chosen =
                candidates.candidates().stream()
                        .filter(candidate -> candidate.legs() != null
                                && candidate.legs().stream()
                                        .allMatch(leg -> recoveryVoyage.equals(
                                                leg.voyageNumber())))
                        .findFirst()
                        .orElseThrow(() -> new BusinessCallFailedException(
                                "組み直し用の航海（" + recoveryVoyage + "）を通る経路候補が"
                                        + "ありません。候補は "
                                        + candidates.candidates().size() + " 件ありました"));

        java.util.List<BusinessMessages.LegRequest> legs = chosen.legs().stream()
                .map(leg -> new BusinessMessages.LegRequest(leg.voyageNumber(),
                        leg.fromUnLocode(), leg.toUnLocode(), leg.departureTime(),
                        leg.arrivalTime()))
                .toList();

        call(ScenarioStep.REDESIGN_ROUTE, () -> gateway.put()
                .uri(RestBusinessGateway.BOOKING_PATH + "/" + bookingId + "/route")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .body(new BusinessMessages.AssignRouteRequest(legs, null))
                .retrieve()
                .toBodilessEntity());
        return BusinessContextKey.NONE;
    }

    private String changeCustomsStatus(ScenarioStep step, String token,
            Map<String, String> context, String status, String reason) {
        call(step, () -> gateway.put()
                .uri(RestBusinessGateway.CUSTOMS_PATH + "/"
                        + required(context, BusinessContextKey.DECLARATION_ID) + "/status")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .body(new BusinessMessages.CustomsStatusRequest(status, reason))
                .retrieve()
                .toBodilessEntity());
        return BusinessContextKey.NONE;
    }

    /** 輸送中のキャンセルを申請する（US30-1）。申請だけでは状態は変わらない。 */
    private String requestCancellation(String token, Map<String, String> context) {
        call(ScenarioStep.REQUEST_CANCELLATION, () -> gateway.post()
                .uri(RestBusinessGateway.BOOKING_PATH + "/"
                        + required(context, BusinessContextKey.BOOKING_ID) + "/cancellation")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .body(new BusinessMessages.RequestCancellationRequest("シミュレーション：荷主都合"))
                .retrieve()
                .toBodilessEntity());
        return BusinessContextKey.NONE;
    }

    /**
     * キャンセルを承認する（US30-5）。
     *
     * <p><strong>陸揚げ地を添える。</strong>添えないと集約が断る（[ADR-025] 決定 4）——
     * 断られること自体は正しい振る舞いであり、こちらの入力が足りていない。
     */
    private String approveCancellation(String token, Map<String, String> context) {
        call(ScenarioStep.APPROVE_CANCELLATION, () -> gateway.put()
                .uri(RestBusinessGateway.BOOKING_PATH + "/"
                        + required(context, BusinessContextKey.BOOKING_ID)
                        + "/cancellation/approve")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .body(new BusinessMessages.ApproveCancellationRequest(
                        RestBusinessGateway.DESTINATION, "シミュレーション：承認"))
                .retrieve()
                .toBodilessEntity());
        return BusinessContextKey.NONE;
    }
}
