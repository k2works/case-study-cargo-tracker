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
            case ISSUE_INVOICE -> invoiceStatusIs(produced, "ISSUED");
            case RECORD_PAYMENT -> invoiceStatusIs(produced, "PAID");
        };
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
