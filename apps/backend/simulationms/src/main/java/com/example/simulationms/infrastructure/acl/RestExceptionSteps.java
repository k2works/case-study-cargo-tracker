package com.example.simulationms.infrastructure.acl;

import static com.example.simulationms.infrastructure.acl.BusinessCalls.bearer;
import static com.example.simulationms.infrastructure.acl.BusinessCalls.call;
import static com.example.simulationms.infrastructure.acl.BusinessCalls.required;

import com.example.simulationms.application.internal.outboundservices.acl.BusinessCallFailedException;
import com.example.simulationms.domain.model.valueobjects.BusinessContextKey;
import com.example.simulationms.domain.model.valueobjects.ScenarioStep;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

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
        return raiseException(token, context, "DELAY",
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

        call(ScenarioStep.RESOLVE_EXCEPTION, () -> gateway.post()
                .uri(RestBusinessGateway.TRACKING_MANAGE_PATH + "/exceptions/"
                        + exceptionId + "/resolve")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .body(new BusinessMessages.ResolveExceptionRequest(trackingNumber, exceptionId,
                        "シミュレーション：対応完了", null))
                .retrieve()
                .toBodilessEntity());
        return BusinessContextKey.NONE;
    }

    /**
     * 現在地から経路を組み直す（US36-3）。
     *
     * <p><strong>出発地は現在地である。</strong>元の出発地から引き直すと、
     * すでに運ばれた区間をもう一度運ぶ経路になる——輸送は再開しない。
     */
    private String redesignRoute(String token, Map<String, String> context) {
        String bookingId = required(context, BusinessContextKey.BOOKING_ID);
        String voyageNumber = required(context, BusinessContextKey.VOYAGE_NUMBER);
        Instant at = clock.instant();

        call(ScenarioStep.REDESIGN_ROUTE, () -> gateway.put()
                .uri(RestBusinessGateway.BOOKING_PATH + "/" + bookingId + "/route")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .body(new BusinessMessages.AssignRouteRequest(java.util.List.of(
                        new BusinessMessages.LegRequest(voyageNumber, WRONG_PORT,
                                RestBusinessGateway.DESTINATION,
                                at.plus(1, ChronoUnit.DAYS).toString(),
                                at.plus(5, ChronoUnit.DAYS).toString())), null))
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
