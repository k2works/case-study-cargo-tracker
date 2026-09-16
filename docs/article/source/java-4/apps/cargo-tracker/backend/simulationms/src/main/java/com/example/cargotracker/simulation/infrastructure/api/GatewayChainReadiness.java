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
            // **用意した便が読めるまで待つ。** 便は投影に現れてから候補の
            // 探索に入るので、待たずに進むと「経路の候補が 1 件もありません」
            // で止まる——用意したのに間に合っていないだけなのに、
            // 「便が無い」と読めてしまう。
            case PREPARE_VOYAGES -> preparedVoyagesAreVisible(produced);
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
            // **荷役の写しも待つ。** 次の工程（荷役の記録）は handlingms が貨物を
            // 知っていることを要る——追跡だけ見て進むと「貨物が見つかりません」で
            // 止まる（実測。同じイベントから別の BC が投影するので、追いつく時刻が
            // 違う）。**待つのは「次の工程が読む場所」である。**
            case ISSUE_TRACKING_NUMBER -> ok(StepRole.TRACKER, "/api/v1/tracking/trackings/"
                    + produced.get(StepKind.ISSUE_TRACKING_NUMBER))
                    && ok(StepRole.HANDLER, "/api/v1/handling/cargos/"
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
            // 起票すると未解決の例外が立つ。**手で起票したものも、荷役や通関から
            // システムが起こしたものも、同じ読み口で確かめる**。
            case REGISTER_EXCEPTION, RECORD_OFF_ROUTE_HANDLING, HOLD_CUSTOMS ->
                    openException(produced) != null;
            // 対応の開始は例外の状態を変える。**追跡の単票から読む**。
            case RESPOND_TO_EXCEPTION -> respondingTo(produced);
            // 解決すると未解決の例外が無くなる。
            case RESOLVE_EXCEPTION -> openException(produced) == null;
            // **前と違う旅程になったかで待つ。**「旅程が入っているか」だと
            // 最初から満たされていて何も確かめない（空振り）。
            case REASSIGN_ROUTE -> itineraryChangedTo(produced);
            // 積込まで済むと輸送中になる。**予約の状態で確かめる**——
            // 承認の要るキャンセルは輸送中にしか起きない。
            case LOAD_CARGO -> "IN_TRANSIT".equals(
                    booking(produced).path("bookingStatus").asText(null));
            // 申請は**承認待ち一覧**（S23）に出る。次の工程（承認）が読む場所で
            // 待つ——履歴で待つと、承認する人に見えていなくても次へ進む。
            case REQUEST_CANCELLATION -> awaitingApproval(produced);
            // 承認すると陸揚げ地が決まり、追跡へ運ばれる。
            case APPROVE_CANCELLATION -> present(tracking(produced),
                    "cancellationDischargeUnLocode");
            // **ここで追跡が閉じる**（US35 §4）。閉じたことが唯一の落とし先である。
            case DISCHARGE_CANCELLED -> tracking(produced).path("closed").asBoolean(false);
        };
    }

    /**
     * 用意した便が航海の一覧から読めるか。
     *
     * <p><b>「既にある便を使う」ときは待たない。</b> 足していないのだから、
     * 待つ相手がいない——既定を「待つ」にすると、何も足さなかった実行が
     * 上限まで待ってから失敗する。</p>
     */
    private boolean preparedVoyagesAreVisible(Map<StepKind, String> produced) {
        String prepared = produced.get(StepKind.PREPARE_VOYAGES);
        if (prepared == null || !prepared.startsWith("V-SIM-")) {
            return true;
        }
        JsonNode voyages = body(StepRole.ROUTING, "/api/v1/routing/voyages?size=200")
                .path("items");
        java.util.Set<String> visible = new java.util.HashSet<>();
        for (JsonNode voyage : voyages) {
            visible.add(voyage.path("voyageNumber").asText());
        }
        return visible.containsAll(java.util.List.of(prepared.split(",")));
    }

    /** 追跡の単票。 */
    private JsonNode tracking(Map<StepKind, String> produced) {
        return body(StepRole.TRACKER, "/api/v1/tracking/trackings/"
                + produced.get(StepKind.ISSUE_TRACKING_NUMBER));
    }

    /**
     * 未解決の例外（無ければ {@code null}）。
     *
     * <p><b>件数の列は単票に出ていない</b>（実測。一覧だけが持つ）。明細から
     * 数える——読み口に無い項目で待つと、いつまでも追いつかない。</p>
     *
     * <p><b>1 本のシナリオに例外は 1 件</b>なので、名指しせずに「開いているもの」
     * で足りる。システムが起こした誤配・税関保留は識別子を返さないので、
     * <b>名指しできるのは手で起票したときだけ</b>である。</p>
     */
    static JsonNode openException(JsonNode tracking) {
        for (JsonNode exception : tracking.path("exceptions")) {
            if (!"RESOLVED".equals(exception.path("responseStatus").asText(null))) {
                return exception;
            }
        }
        return null;
    }

    private JsonNode openException(Map<StepKind, String> produced) {
        return openException(tracking(produced));
    }

    /** 開いている例外の対応が始まったか。 */
    private boolean respondingTo(Map<StepKind, String> produced) {
        JsonNode open = openException(produced);
        return open != null && "RESPONDING".equals(open.path("responseStatus").asText(null));
    }

    /**
     * 承認待ちのキャンセル申請にその予約が出ているか（US35 §受入基準 4）。
     *
     * <p><b>次の工程が読む場所で待つ。</b> 履歴（{@code /cancellation}）で待つと、
     * 承認する人の一覧に出ていなくても次へ進んでしまう。</p>
     */
    private boolean awaitingApproval(Map<StepKind, String> produced) {
        // **その予約の履歴を読む。** 承認待ちの一覧（S23）は追跡管理者の
        // 毎朝の作業一覧なので、シミュレーション由来を既定で外している
        // （[ADR-0020] 決定 4）——そこを待ちに使うと、**自分が作った申請が
        // 自分には見えず**、30 秒待って必ず失敗する（IT17 のクローズで実測）。
        // **待ちは「除外のかかっていない読み口」で判別する。**
        String bookingId = produced.get(StepKind.REGISTER_BOOKING);
        for (JsonNode request : body(StepRole.TRACKER,
                "/api/v1/booking/bookings/" + bookingId + "/cancellation").path("items")) {
            if (!request.hasNonNull("decision")) {
                return true;
            }
        }
        return false;
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
