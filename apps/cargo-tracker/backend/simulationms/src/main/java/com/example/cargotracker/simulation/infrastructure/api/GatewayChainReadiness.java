package com.example.cargotracker.simulation.infrastructure.api;

import com.example.cargotracker.simulation.application.ChainReadiness;
import com.example.cargotracker.simulation.domain.model.valueobjects.StepKind;
import com.example.cargotracker.simulation.domain.model.valueobjects.StepRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/**
 * 連鎖が追いつくまで待つ条件（US33 §受入基準 6）。
 *
 * <p><b>工程ごとに「何が読めたら次へ進めるか」を宣言する。</b> 待ち時間では
 * 判別しない——速い日は無駄に遅く、混んだ日は足りない。</p>
 *
 * <p><b>読むロールは実行するロールと別でよい。</b> 追跡の投影は追跡管理者しか
 * 読めないのに、追跡番号を発行するのは経路設計者である。実行の担当で読もうと
 * すると 403 になり、連鎖が動いていても「追いつかない」と記録される。</p>
 *
 * <p><b>待たない工程も宣言する。</b> 既定を「待つ」にすると、読み口を持たない
 * 工程がすべて上限まで待ってから失敗する。</p>
 */
public class GatewayChainReadiness implements ChainReadiness {

    private final GatewayCalls calls;
    private final ObjectMapper json = new ObjectMapper();

    public GatewayChainReadiness(GatewayCalls calls) {
        this.calls = calls;
    }

    @Override
    public boolean isReady(StepKind kind, Map<StepKind, String> produced) {
        return switch (kind) {
            // 投影が現れるまで 202 が返る（本文は「反映中」）。
            case REGISTER_SHIPPER -> ok(StepRole.SALES, "/api/v1/booking/shippers/"
                    + produced.get(StepKind.REGISTER_SHIPPER));
            case REGISTER_BOOKING -> booking(produced).path("bookingId").isTextual();
            case REQUEST_ROUTING -> present(booking(produced), "routingRequestedAt");
            case ASSIGN_ROUTE -> itineraryAssigned(produced);
            case NOTIFY_SHIPPER -> present(booking(produced), "lastNotifiedAt");
            case CONFIRM_BOOKING -> present(booking(produced), "confirmedAt");
            // 追跡は別サービスの投影。**予約ではなく追跡の読み口で確かめる**
            // ——予約側は発行した時点で埋まるので、連鎖が届いたことにならない。
            case ISSUE_TRACKING_NUMBER -> ok(StepRole.TRACKER, "/api/v1/tracking/trackings/"
                    + produced.get(StepKind.ISSUE_TRACKING_NUMBER));
            case RECORD_HANDLING -> handlingRecorded(produced);
            case CLEAR_CUSTOMS -> "CLEARED".equals(body(StepRole.TRACKER,
                    "/api/v1/handling/customs-declarations/"
                            + produced.get(StepKind.CLEAR_CUSTOMS))
                    .path("status").asText(null));
            // 引取のあと、請求が算出されるまで待つ。**次の工程はそれを読むだけ**。
            case CLAIM_CARGO -> ok(StepRole.ACCOUNTANT, "/api/v1/billing/invoices/by-booking/"
                    + produced.get(StepKind.REGISTER_BOOKING));
            // 読むだけの工程。待つ相手がいない。
            case CALCULATE_INVOICE -> true;
            // **状態の呼び名は請求側の列挙に合わせる**（`BillingStatus.INVOICED`）。
            // 工程の名前（請求書の発行）から推測すると、いつまでも追いつかない。
            case ISSUE_INVOICE -> invoiceStatusIs(produced, "INVOICED");
            case RECORD_PAYMENT -> invoiceStatusIs(produced, "PAID");
            // 例外は起票すると追跡の状態が EXCEPTION へ退避し、件数が増える。
            // **件数で見る**——状態だけだと、対応中に戻ったときに区別できない。
            case REGISTER_EXCEPTION -> openExceptionCount(produced) > 0;
            // 対応の開始は例外の状態を変える。**追跡の単票から読む**。
            case RESPOND_TO_EXCEPTION -> exceptionStatusIs(produced, "RESPONDING");
            // 解決すると未解決の件数が 0 に戻る。
            case RESOLVE_EXCEPTION -> openExceptionCount(produced) == 0;
            // **前と違う旅程になったかで待つ。**「旅程が入っているか」だと
            // 最初から満たされていて何も確かめない（空振り）。
            case REASSIGN_ROUTE -> itineraryChangedTo(produced);
            // 申請は承認待ちになる。**予約の読み口で確かめる**。
            case REQUEST_CANCELLATION -> present(cancellation(produced), "requestedAt");
            // 承認すると陸揚げ地が決まり、追跡へ運ばれる。
            case APPROVE_CANCELLATION -> present(tracking(produced),
                    "cancellationDischargeUnLocode");
            // **ここで追跡が閉じる**（US35 §4）。閉じたことが唯一の落とし先である。
            case DISCHARGE_CANCELLED -> tracking(produced).path("closed").asBoolean(false);
        };
    }

    /** 追跡の単票。 */
    private JsonNode tracking(Map<StepKind, String> produced) {
        return body(StepRole.TRACKER, "/api/v1/tracking/trackings/"
                + produced.get(StepKind.ISSUE_TRACKING_NUMBER));
    }

    /** 未解決の例外の件数。<b>状態ではなく件数で見る</b>。 */
    private int openExceptionCount(Map<StepKind, String> produced) {
        return tracking(produced).path("openExceptionCount").asInt(0);
    }

    /** 起票した例外の対応状態。 */
    private boolean exceptionStatusIs(Map<StepKind, String> produced, String status) {
        String exceptionId = produced.get(StepKind.REGISTER_EXCEPTION);
        for (JsonNode exception : tracking(produced).path("exceptions")) {
            if (exceptionId != null && exceptionId.equals(exception.path("exceptionId").asText())) {
                return status.equals(exception.path("responseStatus").asText(null));
            }
        }
        return false;
    }

    /** キャンセル申請の読み口。 */
    private JsonNode cancellation(Map<StepKind, String> produced) {
        return body(StepRole.SALES, "/api/v1/booking/bookings/"
                + produced.get(StepKind.REGISTER_BOOKING) + "/cancellation");
    }

    /**
     * 組み直したあとの旅程が、工程が確定したものと一致したか（US35 §受入基準 3）。
     *
     * <p><b>「変わったか」ではなく「そうなったか」で見る。</b> 変化だけを見ると、
     * 別の誰かが同時に変えても満たされる。</p>
     */
    private boolean itineraryChangedTo(Map<StepKind, String> produced) {
        String expected = produced.get(StepKind.REASSIGN_ROUTE);
        JsonNode legs = body(StepRole.ROUTING, "/api/v1/booking/bookings/"
                + produced.get(StepKind.REGISTER_BOOKING) + "/itinerary").path("legs");
        return expected != null
                && expected.equals(GatewayResponses.fingerprintOf(legs));
    }

    private boolean itineraryAssigned(Map<StepKind, String> produced) {
        JsonNode legs = body(StepRole.ROUTING, "/api/v1/booking/bookings/"
                + produced.get(StepKind.REGISTER_BOOKING) + "/itinerary").path("legs");
        return legs.isArray() && !legs.isEmpty();
    }

    private boolean handlingRecorded(Map<StepKind, String> produced) {
        JsonNode activities = body(StepRole.HANDLER, "/api/v1/handling/"
                + produced.get(StepKind.ISSUE_TRACKING_NUMBER) + "/activities")
                .path("items");
        // 受領・積込・荷降しの 3 件。**件数で見る**——最後の 1 件だけを見ると、
        // 途中が落ちていても通ってしまう。
        return activities.isArray() && activities.size() >= 3;
    }

    private boolean invoiceStatusIs(Map<StepKind, String> produced, String status) {
        return status.equals(body(StepRole.ACCOUNTANT, "/api/v1/billing/invoices/"
                + produced.get(StepKind.CALCULATE_INVOICE)).path("status").asText(null));
    }

    private JsonNode booking(Map<StepKind, String> produced) {
        return body(StepRole.SALES, "/api/v1/booking/bookings/"
                + produced.get(StepKind.REGISTER_BOOKING));
    }

    private static boolean present(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return !value.isMissingNode() && !value.isNull();
    }

    private boolean ok(StepRole role, String uri) {
        return calls.get(role, uri).status() == 200;
    }

    private JsonNode body(StepRole role, String uri) {
        GatewayCalls.Response response = calls.get(role, uri);
        try {
            return json.readTree(response.body() == null ? "{}" : response.body());
        } catch (java.io.IOException e) {
            return json.createObjectNode();
        }
    }
}
