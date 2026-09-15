package com.example.cargotracker.simulation.infrastructure.api;

import static com.example.cargotracker.simulation.infrastructure.api.GatewayResponses.businessReason;
import static com.example.cargotracker.simulation.infrastructure.api.GatewayResponses.fingerprintOf;
import static com.example.cargotracker.simulation.infrastructure.api.GatewayResponses.parse;

import com.example.cargotracker.simulation.application.BusinessApi.StepResult;
import com.example.cargotracker.simulation.domain.model.valueobjects.ScenarioInput;
import com.example.cargotracker.simulation.domain.model.valueobjects.StepKind;
import com.example.cargotracker.simulation.domain.model.valueobjects.StepRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 例外と輸送中キャンセルの工程（US35）。
 *
 * <p><b>正常系（{@link GatewayBusinessApi}）と分ける。</b> 予約から精算までの
 * 連なりと、起きてしまったことへの対応は読みどころが違う——1 つに積むと
 * どちらも埋もれる。</p>
 *
 * <p><b>手で起票できない例外がある。</b> 誤配は荷役が、税関保留は通関が決める
 * ことなので、実物は手での起票を断る（実測）。<b>本番と同じ出来事から起こす</b>
 * ——経路外の荷役、通関の留置である。</p>
 */
final class GatewayRecoverySteps {

    private final GatewayCalls calls;
    private final ScenarioInput input;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();

    /** 実行ごとの目印（記録に残す名前に混ぜる）。 */
    private final String tag = UUID.randomUUID().toString().substring(0, 8);

    GatewayRecoverySteps(GatewayCalls calls, ScenarioInput input, Clock clock) {
        this.calls = calls;
        this.input = input;
        this.clock = clock;
    }

    /** 例外・キャンセルの工程を実行する。 */
    StepResult execute(StepKind kind, Map<StepKind, String> produced) {
        return switch (kind) {
            case REGISTER_EXCEPTION -> registerException(kind, produced);
            case RECORD_OFF_ROUTE_HANDLING -> recordOffRouteHandling(kind, produced);
            case HOLD_CUSTOMS -> holdCustoms(kind, produced);
            case REASSIGN_ROUTE -> reassignRoute(kind, produced);
            case RESPOND_TO_EXCEPTION -> respondToException(kind, produced);
            case RESOLVE_EXCEPTION -> resolveException(kind, produced);
            case LOAD_CARGO -> loadCargo(kind, produced);
            case REQUEST_CANCELLATION -> requestCancellation(kind, produced);
            case APPROVE_CANCELLATION -> approveCancellation(kind, produced);
            case DISCHARGE_CANCELLED -> dischargeCancelled(kind, produced);
            // **正常系はここに来ない。** 来たら配線の誤りなので、黙って
            // 成功にせず理由を言って止まる。
            default -> StepResult.failure(
                    "この担い手が扱わない工程です: " + kind.label());
        };
    }

    /** 通関の申告を出す担当。<b>現場（荷役作業員）である</b>（US29 §受入基準 1）。 */
    private static final StepRole DECLARES_CUSTOMS = StepRole.HANDLER;

    /**
     * 受領と積込を記録して、貨物を輸送中にする（US35 §受入基準 4）。
     *
     * <p><b>荷降しは記録しない。</b> 記録すると輸送が終わり、承認の要らない
     * キャンセルになる——確かめたいのは輸送中のキャンセルである。</p>
     */
    private StepResult loadCargo(StepKind kind, Map<StepKind, String> produced) {
        JsonNode legs = GatewayHandling.legsOf(calls, produced);
        if (!legs.isArray() || legs.isEmpty()) {
            return StepResult.failure("確定した旅程が読めませんでした（積込の港を決められません）");
        }
        JsonNode first = legs.get(0);
        String trackingNumber = produced.get(StepKind.ISSUE_TRACKING_NUMBER);
        String port = first.path("loadUnLocode").asText();
        var received = GatewayHandling.register(calls, clock, kind, trackingNumber,
                "RECEIVE", port, null, null);
        if (!received.ok()) {
            return StepResult.failure(received.status(), businessReason(received));
        }
        var loaded = GatewayHandling.register(calls, clock, kind, trackingNumber,
                "LOAD", port, first.path("voyageNumber").asText(), null);
        return loaded.ok() ? StepResult.success(null)
                : StepResult.failure(loaded.status(), businessReason(loaded));
    }

    private StepResult requestCancellation(StepKind kind, Map<StepKind, String> produced) {
        var response = calls.post(StepRole.of(kind),
                GatewayHandling.bookingUri(produced, "/cancellation"),
                Map.of("reason", "業務シミュレーション（輸送中キャンセル）"));
        return response.ok() ? StepResult.success(null)
                : StepResult.failure(response.status(), businessReason(response));
    }

    /**
     * 現在地からの経路を組み直す（US35 §受入基準 3）。
     *
     * <p><b>叩く API は経路の確定と同じ。</b> 専用の経路を作ると、実際の再設計で
     * 通らない道を確かめることになる。違うのは<b>待ち方</b>だけである。</p>
     *
     * <p><b>同じ旅程を選び直さない。</b> 候補の 1 件目が前と同じなら 2 件目を取る
     * ——同じものに戻すと、組み直したことにならない。</p>
     */
    private StepResult reassignRoute(StepKind kind, Map<StepKind, String> produced) {
        var candidates = calls.get(StepRole.of(kind),
                GatewayHandling.bookingUri(produced, "/route-candidates"));
        if (!candidates.ok()) {
            return StepResult.failure(candidates.status(), businessReason(candidates));
        }
        String before = produced.get(StepKind.ASSIGN_ROUTE);
        JsonNode all = parse(candidates).path("candidates");
        JsonNode chosen = null;
        for (JsonNode candidate : all) {
            JsonNode legs = candidate.path("legs");
            if (legs.isArray() && !legs.isEmpty()
                    && !fingerprintOf(legs).equals(before)) {
                chosen = legs;
                break;
            }
        }
        if (chosen == null) {
            return StepResult.failure(422,
                    "前と違う経路の候補がありません（組み直す先がありません）");
        }
        var assigned = calls.post(StepRole.of(kind), GatewayHandling.bookingUri(produced, "/route"),
                Map.of("legs", json.convertValue(chosen, List.class)));
        return assigned.ok() ? StepResult.success(fingerprintOf(chosen))
                : StepResult.failure(assigned.status(), businessReason(assigned));
    }

    /**
     * 例外を起票する（US35 §受入基準 1・2）。
     *
     * <p><b>種別はシナリオが持つ。</b> 工程に持たせると、種類の数だけ工程が増える
     * ——扱っていない場所が名乗り出ないまま列挙が肥大する（注 N10）。</p>
     */
    private StepResult registerException(StepKind kind, Map<StepKind, String> produced) {
        String exceptionType = input.scenario().exceptionType();
        if (exceptionType == null) {
            // **シナリオの宣言と工程の並びが食い違っている。** 黙って別の種別で
            // 起票すると、確かめたいものと違うものが通る。
            return StepResult.failure(
                    "シナリオ「" + input.scenario().label() + "」に例外種別がありません");
        }
        var response = calls.post(StepRole.of(kind), "/api/v1/tracking/trackings/"
                + produced.get(StepKind.ISSUE_TRACKING_NUMBER) + "/exceptions", Map.of(
                "exceptionType", exceptionType,
                "description", "業務シミュレーション: " + input.scenario().label()));
        if (!response.ok()) {
            return StepResult.failure(response.status(), businessReason(response));
        }
        String exceptionId = parse(response).path("exceptionId").asText(null);
        return exceptionId == null || exceptionId.isBlank()
                ? StepResult.failure(response.status(),
                        "応答に exceptionId がありません: " + response.body())
                : StepResult.success(exceptionId);
    }

    /**
     * キャンセルを承認し、陸揚げ地を指定する（US35 §受入基準 4）。
     *
     * <p><b>陸揚げ地は候補から選ぶ。</b> 決め打ちにすると、経路が変わったときに
     * 「その航海が寄らない港で降ろす」ことになり、業務が断る。</p>
     */
    private StepResult approveCancellation(StepKind kind, Map<StepKind, String> produced) {
        var candidates = calls.get(StepRole.of(kind),
                GatewayHandling.bookingUri(produced, "/cancellation/discharge-candidates"));
        if (!candidates.ok()) {
            return StepResult.failure(candidates.status(), businessReason(candidates));
        }
        // **応答の項目名を推測しない。** 読み口が返すのは `unLocodes`（港の符号の
        // 並び）である——`candidates` という名で読んでいて「候補が 0 件」に
        // 見えていた（実測）。読み口の形は実物で確かめる。
        JsonNode ports = parse(candidates).path("unLocodes");
        if (!ports.isArray() || ports.isEmpty()) {
            return StepResult.failure("陸揚げ地の候補が 1 件もありません");
        }
        String unLocode = ports.get(0).path("unLocode").asText(null);
        if (unLocode == null) {
            unLocode = ports.get(0).asText();
        }
        var approved = calls.post(StepRole.of(kind),
                GatewayHandling.bookingUri(produced, "/cancellation/approval"),
                Map.of("dischargeUnLocode", unLocode, "reason", "業務シミュレーション"));
        // **指定した港を次の工程へ渡す。** 渡さないと、荷降しの港をもう一度
        // 当てることになり、承認と荷降しが別の港を指しうる。
        return approved.ok() ? StepResult.success(unLocode)
                : StepResult.failure(approved.status(), businessReason(approved));
    }

    /** 指定された港で荷降しする。<b>ここで追跡が閉じる</b>（US35 §受入基準 4）。 */
    private StepResult dischargeCancelled(StepKind kind, Map<StepKind, String> produced) {
        String unLocode = produced.get(StepKind.APPROVE_CANCELLATION);
        if (unLocode == null) {
            return StepResult.failure("承認で指定した陸揚げ地が読めませんでした");
        }
        // **航海は「その港に触れる区間」から選ぶ。** 降ろす港だけを見ると、
        // 承認が**現在地**を選んだとき（積んだ港で降ろす）に見つからず、
        // 「航海番号が必要です」で止まる（実測）。積む港も候補に入れる。
        JsonNode legs = GatewayHandling.legsOf(calls, produced);
        String voyageNumber = voyageTouching(legs, unLocode, "unloadUnLocode");
        if (voyageNumber == null) {
            voyageNumber = voyageTouching(legs, unLocode, "loadUnLocode");
        }
        if (voyageNumber == null) {
            return StepResult.failure(
                    "指定された陸揚げ地 " + unLocode + " に触れる区間が旅程にありません");
        }
        var response = GatewayHandling.register(calls, clock, kind, produced.get(StepKind.ISSUE_TRACKING_NUMBER),
                "UNLOAD", unLocode, voyageNumber, null);
        return response.ok() ? StepResult.success(unLocode)
                : StepResult.failure(response.status(), businessReason(response));
    }

    /**
     * 経路外の港で荷役を記録し、システムに誤配を起票させる（US35 §受入基準 3）。
     *
     * <p><b>手で起票しない</b>（実物が断る）。荷役が決めることなので、本番と
     * 同じ出来事から起こす。<b>港は旅程に無いものを選ぶ</b>——旅程の港で記録
     * すると誤配にならない。</p>
     */
    private StepResult recordOffRouteHandling(StepKind kind, Map<StepKind, String> produced) {
        JsonNode legs = GatewayHandling.legsOf(calls, produced);
        java.util.Set<String> onRoute = new java.util.HashSet<>();
        for (JsonNode leg : legs) {
            onRoute.add(leg.path("loadUnLocode").asText());
            onRoute.add(leg.path("unloadUnLocode").asText());
        }
        // **便が通っている港から選ぶ。** 固定の一覧から選ぶと、その港に便が
        // 無い環境では「組み直す先がない」で次の工程が止まる——確かめたいのは
        // 誤配の対応であって、港の品揃えではない（実測で踏んだ）。
        String offRoute = servedPorts().stream()
                .filter(port -> !onRoute.contains(port))
                .findFirst()
                .orElse(null);
        if (offRoute == null || legs.isEmpty()) {
            return StepResult.failure("旅程に無い港が見つかりませんでした（誤配を起こせません）");
        }
        var response = GatewayHandling.register(calls, clock, kind, produced.get(StepKind.ISSUE_TRACKING_NUMBER),
                "UNLOAD", offRoute, legs.get(0).path("voyageNumber").asText(), null);
        return response.ok() ? StepResult.success(offRoute)
                : StepResult.failure(response.status(), businessReason(response));
    }

    /**
     * 通関を留置し、システムに税関保留を起票させる（US35 §受入基準 1）。
     *
     * <p><b>手で起票しない</b>（実物が断る）。通関が決めることだからである。</p>
     */
    private StepResult holdCustoms(StepKind kind, Map<StepKind, String> produced) {
        String declarationNumber = "SIM-" + tag + "-" + UUID.randomUUID().toString()
                .substring(0, 8);
        var registered = calls.post(DECLARES_CUSTOMS,
                "/api/v1/handling/customs-declarations", Map.of(
                        "declarationNumber", declarationNumber,
                        "trackingNumber", produced.get(StepKind.ISSUE_TRACKING_NUMBER),
                        "declaredAt", clock.instant().toString()));
        if (!registered.ok()) {
            return StepResult.failure(registered.status(), businessReason(registered));
        }
        var held = calls.post(StepRole.of(kind),
                "/api/v1/handling/customs-declarations/" + declarationNumber + "/status",
                Map.of("status", "HELD", "reason", "業務シミュレーション（税関保留）"));
        return held.ok() ? StepResult.success(declarationNumber)
                : StepResult.failure(held.status(), businessReason(held));
    }

    private StepResult respondToException(StepKind kind, Map<StepKind, String> produced) {
        return onOpenException(kind, produced, "/response",
                Map.of("plan", input.scenario().label() + "の対応を始めます"));
    }

    private StepResult resolveException(StepKind kind, Map<StepKind, String> produced) {
        return onOpenException(kind, produced, "/resolution",
                Map.of("resolution", input.scenario().label() + "を解決しました"));
    }

    /**
     * 開いている例外に対して操作する。
     *
     * <p><b>識別子は読み口から取る。</b> 手で起票したものは応答で返るが、
     * <b>システムが起こした誤配・税関保留は識別子を返さない</b>——どちらも
     * 同じ形で扱えるように、追跡の単票から「開いている例外」を引く。
     * 1 本のシナリオに例外は 1 件なので、名指しせずに足りる。</p>
     */
    private StepResult onOpenException(StepKind kind, Map<StepKind, String> produced,
            String suffix, Object body) {
        var tracking = calls.get(StepRole.of(kind), "/api/v1/tracking/trackings/"
                + produced.get(StepKind.ISSUE_TRACKING_NUMBER));
        if (!tracking.ok()) {
            return StepResult.failure(tracking.status(), businessReason(tracking));
        }
        JsonNode open = GatewayChainReadiness.openException(parse(tracking));
        if (open == null) {
            return StepResult.failure("未解決の例外がありません（起票が届いていません）");
        }
        String exceptionId = open.path("exceptionId").asText(null);
        var response = calls.post(StepRole.of(kind), "/api/v1/tracking/trackings/"
                + produced.get(StepKind.ISSUE_TRACKING_NUMBER)
                + "/exceptions/" + exceptionId + suffix, body);
        return response.ok() ? StepResult.success(exceptionId)
                : StepResult.failure(response.status(), businessReason(response));
    }

    /**
     * 便が通っている港。
     *
     * <p><b>登録されている航海から読む。</b> 固定の一覧にすると、環境ごとの
     * 品揃えの違いで「知らない港」や「便が無い」に化ける。</p>
     */
    private java.util.List<String> servedPorts() {
        var response = calls.get(StepRole.ROUTING, "/api/v1/routing/voyages?size=200");
        java.util.LinkedHashSet<String> ports = new java.util.LinkedHashSet<>();
        for (JsonNode voyage : parse(response).path("items")) {
            for (JsonNode movement : voyage.path("movements")) {
                ports.add(movement.path("departureUnLocode").asText());
                ports.add(movement.path("arrivalUnLocode").asText());
            }
        }
        return java.util.List.copyOf(ports);
    }

    /** その港に触れる区間の航海番号（無ければ {@code null}）。 */
    private static String voyageTouching(JsonNode legs, String unLocode, String field) {
        for (JsonNode leg : legs) {
            if (unLocode.equals(leg.path(field).asText())) {
                return leg.path("voyageNumber").asText();
            }
        }
        return null;
    }
}
