package com.example.cargotracker.simulation.infrastructure.api;

import com.example.cargotracker.simulation.application.BusinessApi;
import com.example.cargotracker.simulation.domain.model.valueobjects.Scenario;
import com.example.cargotracker.simulation.domain.model.valueobjects.StepKind;
import com.example.cargotracker.simulation.domain.model.valueobjects.StepRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.UUID;

/**
 * 工程を Gateway の経路へ写す（[ADR-0020] 決定 2）。
 *
 * <p><b>1 実行に 1 つ作る。</b> 作った識別子（荷主・予約）は実行の中でしか
 * 意味を持たず、トークンも実行をまたいで持たない。</p>
 *
 * <p><b>段取りはここに無い。</b> どの工程をどの順に実行するかは {@link Scenario}
 * が持つ。ここにあるのは「その工程はどの経路をどのロールで叩くか」だけである。</p>
 */
public class GatewayBusinessApi implements BusinessApi {

    /** 業務タイムゾーン。<b>UTC で「今日」を決めない</b>（IT9 の教訓）。 */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Tokyo");

    /** 標準シナリオの出発地・目的地。**便のある組み合わせを使う**。 */
    private static final String ORIGIN = "JPTYO";
    private static final String DESTINATION = "USNYC";

    /** 経路が組める余裕（標準シナリオ）。 */
    private static final int STANDARD_DEADLINE_DAYS = 120;

    /**
     * 便が 1 本も結ばない目的地（NO_ROUTE シナリオ）。
     *
     * <p><b>期限の短さで作らない。</b> 最初は「期限を 1 日にすれば候補が無くなる」
     * と考えたが、<b>クラスタには過去の実行が登録した便が溜まる</b>ので、たまたま
     * 間に合う便があると成功してしまう（実測）。<b>時間ではなく構造で決める</b>
     * ——どの便も寄らない港を目的地にすれば、候補は必ず 0 件になる。</p>
     *
     * <p><b>実在の港を使う</b>（南極・マクマード基地）。架空の符号にすると、
     * 断りが「経路が無い」ではなく「知らない港」に化けることがある。</p>
     */
    private static final String UNSERVED_DESTINATION = "AQMCM";

    /** 自分が作ったものが読めるようになるまで読み直す回数。 */
    private static final int DEFAULT_ID_READ_ATTEMPTS = 60;

    /** 読み直す間隔。 */
    private static final long ID_READ_INTERVAL_MS = 500;

    /**
     * 別サービスの断りを包む言い回し。
     *
     * <p><b>間の字面を決め打ちしない。</b> 実測では {@code failed.\nCaused by }
     * だったが、版によって {@code failed: } にも {@code failed. Caused by } にも
     * なる。<b>最後の「原因」以降だけ</b>を人が読む一節として扱う。</p>
     */
    private static final Pattern WRAPPER =
            Pattern.compile("(?s).*(?:Caused by|failed:)\\s*");

    /**
     * 通関の申告を出す担当。
     *
     * <p><b>工程の担当と違う唯一の場所である</b>（US29 §受入基準 1）。申告を出すのは
     * 現場（荷役作業員）で、状態を通関済に更新するのは追跡管理者——1 つの工程が
     * 2 つの担当にまたがる。<b>ここだけは {@link StepRole#of} で答えられない</b>ので、
     * 名前を付けて例外であることを見えるようにする。</p>
     */
    private static final StepRole DECLARES_CUSTOMS = StepRole.HANDLER;

    private final GatewayCalls calls;
    private final Scenario scenario;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();
    private final String tag = UUID.randomUUID().toString().substring(0, 8);

    private final int idReadAttempts;

    public GatewayBusinessApi(GatewayCalls calls, Scenario scenario, Clock clock) {
        this(calls, scenario, clock, DEFAULT_ID_READ_ATTEMPTS);
    }

    /**
     * 読み直す回数を決めて作る。
     *
     * <p><b>検査は待たない。</b> 実際に眠って確かめると、1 本の検査に 30 秒かかる
     * ——そのぶん誰も回さなくなる。待つ回数のほうを変える。</p>
     */
    GatewayBusinessApi(GatewayCalls calls, Scenario scenario, Clock clock, int idReadAttempts) {
        this.calls = calls;
        this.scenario = scenario;
        this.clock = clock;
        this.idReadAttempts = idReadAttempts;
    }

    @Override
    public StepResult execute(StepKind kind, Map<StepKind, String> produced) {
        return switch (kind) {
            case REGISTER_SHIPPER -> registerShipper(kind);
            case REGISTER_BOOKING -> registerBooking(kind, produced);
            case REQUEST_ROUTING -> simplePost(kind, bookingUri(produced, "/routing-request"),
                    null);
            case ASSIGN_ROUTE -> assignRoute(kind, produced);
            case NOTIFY_SHIPPER -> simplePost(kind, bookingUri(produced, "/notifications"),
                    Map.of("recipientEmail", email(), "summary", "確定した経路をお知らせします"));
            case CONFIRM_BOOKING -> simplePost(kind, bookingUri(produced, "/confirmation"),
                    null);
            case ISSUE_TRACKING_NUMBER -> issueTrackingNumber(kind, produced);
            case RECORD_HANDLING -> recordHandling(kind, produced);
            case CLEAR_CUSTOMS -> clearCustoms(kind, produced);
            case CLAIM_CARGO -> claimCargo(kind, produced);
            case CALCULATE_INVOICE -> readInvoice(kind, produced);
            case ISSUE_INVOICE -> simplePost(kind,
                    "/api/v1/billing/invoices/" + produced.get(StepKind.CALCULATE_INVOICE)
                            + "/issue", null);
            case RECORD_PAYMENT -> recordPayment(kind, produced);
        };
    }

    private StepResult registerShipper(StepKind kind) {
        var response = calls.post(StepRole.of(kind), "/api/v1/booking/shippers", Map.of(
                "name", "シミュレーション商事 " + tag,
                "shipperType", "INDIVIDUAL",
                "email", email(),
                "phone", "03-0000-0000",
                "address", "東京都港区",
                // **印はここで付く**（US33 §受入基準 3）。落とすと 4 つの BC が
                // 本物と区別できなくなる。
                "simulated", true));
        return idFrom(response, "shipperId");
    }

    private StepResult registerBooking(StepKind kind, Map<StepKind, String> produced) {
        LocalDate deadline = LocalDate.now(clock.withZone(BUSINESS_ZONE))
                .plusDays(STANDARD_DEADLINE_DAYS);
        String destination = scenario == Scenario.NO_ROUTE
                ? UNSERVED_DESTINATION : DESTINATION;
        // **Map.of は 10 組までしか取れない。** 溢れた項目は黙って落ちるのではなく
        // 書けなくなるだけだが、ここは項目が増えるので順序付きの地図で組む
        // （必須の `quantity` を落として 400 で止まった。IT16 で実測）。
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("shipperId", produced.get(StepKind.REGISTER_SHIPPER));
        body.put("originUnLocode", ORIGIN);
        body.put("destinationUnLocode", destination);
        body.put("arrivalDeadline", deadline.toString());
        body.put("cargoType", "GENERAL");
        body.put("weightKg", 1000);
        body.put("lengthCm", 100);
        body.put("widthCm", 100);
        body.put("heightCm", 100);
        body.put("quantity", 10);
        body.put("productName", "シミュレーション貨物 " + tag);
        var response = calls.post(StepRole.of(kind), "/api/v1/booking/bookings", body);
        return idFrom(response, "bookingId");
    }

    /**
     * 経路を確定する。
     *
     * <p><b>候補 0 件を「通信の失敗」と言い分ける。</b> NO_ROUTE シナリオが
     * 確かめたいのはここで、「経路が無い」と読めなければ US34 の目的を果たさない。</p>
     */
    private StepResult assignRoute(StepKind kind, Map<StepKind, String> produced) {
        var candidates = calls.get(StepRole.of(kind),
                bookingUri(produced, "/route-candidates"));
        if (!candidates.ok()) {
            return StepResult.failure(candidates.status(), businessReason(candidates));
        }
        JsonNode legs = parse(candidates).path("candidates").path(0).path("legs");
        if (!legs.isArray() || legs.isEmpty()) {
            return StepResult.failure(422,
                    "期限に間に合う経路の候補が 1 件もありません"
                            + "（条件を調整するか、便を増やしてください）");
        }
        var assigned = calls.post(StepRole.of(kind), bookingUri(produced, "/route"),
                Map.of("legs", json.convertValue(legs, List.class)));
        return assigned.ok() ? StepResult.success(null)
                : StepResult.failure(assigned.status(), assigned.body());
    }

    private StepResult issueTrackingNumber(StepKind kind, Map<StepKind, String> produced) {
        var issued = calls.post(StepRole.of(kind), bookingUri(produced, "/tracking-number"),
                null);
        if (!issued.ok()) {
            return StepResult.failure(issued.status(), issued.body());
        }
        // **番号は応答に載らない**（予約 ID だけが返る）。読み口から取る——
        // 取れないと後続の工程が貨物を名指しできない。
        //
        // **1 度読んで諦めない。** 予約の投影も結果整合で、発行の直後は
        // まだ空である。工程のあいだの待ちは ChainReadiness が見るが、
        // 工程が自分の結果を読むときの待ちは、その工程が持つしかない。
        for (int attempt = 0; attempt < idReadAttempts; attempt++) {
            var booking = calls.get(StepRole.of(kind), bookingUri(produced, ""));
            String trackingNumber = parse(booking).path("trackingNumber").asText(null);
            if (trackingNumber != null && !trackingNumber.isBlank()) {
                return StepResult.success(trackingNumber);
            }
            sleepBriefly();
        }
        return StepResult.failure(
                "追跡番号を発行しましたが、予約の読み口に現れませんでした"
                        + "（投影が止まっている可能性があります）");
    }

    /**
     * 受領・積込・荷降しを順に記録する。
     *
     * <p><b>旅程から港と便を取る。</b> 決め打ちにすると、経路が変わったときに
     * 「経路外の荷役」として記録され、確かめたい連鎖と違うものが動く。</p>
     */
    private StepResult recordHandling(StepKind kind, Map<StepKind, String> produced) {
        JsonNode legs = legsOf(produced);
        if (!legs.isArray() || legs.isEmpty()) {
            return StepResult.failure(
                    "確定した旅程が読めませんでした（荷役の港と便を決められません）");
        }
        JsonNode first = legs.get(0);
        JsonNode last = legs.get(legs.size() - 1);
        String trackingNumber = produced.get(StepKind.ISSUE_TRACKING_NUMBER);
        record Activity(String type, String unLocode, String voyageNumber) {
        }
        for (Activity activity : List.of(
                new Activity("RECEIVE", first.path("loadUnLocode").asText(), null),
                new Activity("LOAD", first.path("loadUnLocode").asText(),
                        first.path("voyageNumber").asText()),
                new Activity("UNLOAD", last.path("unloadUnLocode").asText(),
                        last.path("voyageNumber").asText()))) {
            var response = registerActivity(kind, trackingNumber, activity.type(),
                    activity.unLocode(), activity.voyageNumber(), null);
            if (!response.ok()) {
                return StepResult.failure(response.status(),
                        activity.type() + ": " + response.body());
            }
        }
        return StepResult.success(null);
    }

    /**
     * 通関を通す。
     *
     * <p><b>担当が 2 つに分かれる。</b> 申告を出すのは現場（荷役作業員）、
     * 状態を更新するのは追跡管理者である（US29 §受入基準 1・2）。
     * 片方に寄せると、実際には通らない経路が通ってしまう。</p>
     */
    private StepResult clearCustoms(StepKind kind, Map<StepKind, String> produced) {
        String declarationNumber = "SIM-" + tag + "-" + UUID.randomUUID().toString()
                .substring(0, 8);
        var registered = calls.post(DECLARES_CUSTOMS,
                "/api/v1/handling/customs-declarations", Map.of(
                        "declarationNumber", declarationNumber,
                        "trackingNumber", produced.get(StepKind.ISSUE_TRACKING_NUMBER),
                        // **申告日時は必須**（集約が断る）。省くと通関で止まる。
                        "declaredAt", clock.instant().toString()));
        if (!registered.ok()) {
            return StepResult.failure(registered.status(), registered.body());
        }
        var cleared = calls.post(StepRole.of(kind),
                "/api/v1/handling/customs-declarations/" + declarationNumber + "/status",
                Map.of("status", "CLEARED", "reason", "業務シミュレーション"));
        return cleared.ok() ? StepResult.success(declarationNumber)
                : StepResult.failure(cleared.status(), cleared.body());
    }

    /**
     * 引取を記録する。
     *
     * <p><b>港は旅程から取る</b>（荷役と同じ）。決め打ちにすると、経路が変わった
     * ときに「経路外での引取」になり、確かめたい連鎖と違うものが動く。</p>
     */
    private StepResult claimCargo(StepKind kind, Map<StepKind, String> produced) {
        JsonNode legs = legsOf(produced);
        if (!legs.isArray() || legs.isEmpty()) {
            return StepResult.failure("確定した旅程が読めませんでした（引取の港を決められません）");
        }
        String destination = legs.get(legs.size() - 1).path("unloadUnLocode").asText();
        var response = registerActivity(kind, produced.get(StepKind.ISSUE_TRACKING_NUMBER),
                "CLAIM", destination, null, "シミュレーション荷受人 " + tag);
        return response.ok() ? StepResult.success(null)
                : StepResult.failure(response.status(), response.body());
    }

    /** 確定した旅程の区間。<b>荷役も引取もここから港を決める</b>。 */
    private JsonNode legsOf(Map<StepKind, String> produced) {
        return parse(calls.get(StepRole.ROUTING, bookingUri(produced, "/itinerary")))
                .path("legs");
    }

    /** 連鎖が作った請求を読む。<b>ここでは作らない</b>——作れてしまうと連鎖を確かめない。 */
    private StepResult readInvoice(StepKind kind, Map<StepKind, String> produced) {
        var response = calls.get(StepRole.of(kind), "/api/v1/billing/invoices/by-booking/"
                + produced.get(StepKind.REGISTER_BOOKING));
        return idFrom(response, "invoiceId");
    }

    private StepResult recordPayment(StepKind kind, Map<StepKind, String> produced) {
        String invoiceId = produced.get(StepKind.CALCULATE_INVOICE);
        var invoice = calls.get(StepRole.of(kind), "/api/v1/billing/invoices/" + invoiceId);
        JsonNode amount = parse(invoice).path("totalAmount");
        if (amount.isMissingNode() || amount.isNull()) {
            return StepResult.failure(invoice.status(),
                    "請求金額が読めませんでした（入金の額を決められません）");
        }
        var response = calls.post(StepRole.of(kind),
                "/api/v1/billing/invoices/" + invoiceId + "/payments", Map.of(
                        "amount", amount.decimalValue(),
                        "paidAt", clock.instant().toString()));
        return response.ok() ? StepResult.success(null)
                : StepResult.failure(response.status(), response.body());
    }

    private GatewayCalls.Response registerActivity(StepKind kind, String trackingNumber,
            String type, String unLocode, String voyageNumber, String consigneeName) {
        var body = new java.util.HashMap<String, Object>();
        // **冪等キーはこちらが作る**（サーバは採らない）。再送で二重に記録しない。
        body.put("activityId", UUID.randomUUID().toString());
        body.put("trackingNumber", trackingNumber);
        body.put("handlingType", type);
        body.put("unLocode", unLocode);
        body.put("voyageNumber", voyageNumber);
        body.put("consigneeName", consigneeName);
        body.put("completedAt", clock.instant().toString());
        return calls.post(StepRole.of(kind), "/api/v1/handling/activities", body);
    }

    /** 送って通ったかだけを見る工程。<b>生成するものを持たない</b>。 */
    private StepResult simplePost(StepKind kind, String uri, Object body) {
        var response = calls.post(StepRole.of(kind), uri, body);
        return response.ok() ? StepResult.success(null)
                : StepResult.failure(response.status(), response.body());
    }

    private StepResult idFrom(GatewayCalls.Response response, String field) {
        if (!response.ok()) {
            return StepResult.failure(response.status(), response.body());
        }
        String id = parse(response).path(field).asText(null);
        return id == null || id.isBlank()
                ? StepResult.failure(response.status(),
                        "応答に " + field + " がありません: " + response.body())
                : StepResult.success(id);
    }

    private String bookingUri(Map<StepKind, String> produced, String suffix) {
        return "/api/v1/booking/bookings/" + produced.get(StepKind.REGISTER_BOOKING) + suffix;
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(ID_READ_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("待ちが中断されました", e);
        }
    }

    /**
     * 断りの理由のうち、人が読む部分を取り出す。
     *
     * <p><b>内部の言葉をそのまま出さない。</b> 経路の問い合わせは別サービスへ
     * 渡るので、断りが「An exception was thrown by the remote message handling
     * component: Handling query with identifier [...] failed: 〜」という形で
     * 包まれて返る（IT16 のクラスタで実測）。<b>読む人が要るのは最後の一節</b>
     * ——「その港を通る航海が登録されていません: AQMCM」である。</p>
     */
    private String businessReason(GatewayCalls.Response response) {
        String message = parse(response).path("message").asText(null);
        if (message == null || message.isBlank()) {
            return response.body();
        }
        // **包み方を字面で決め打ちしない。** 区切りの前後の空白も改行も版で変わる。
        return WRAPPER.matcher(message).replaceFirst("").trim();
    }

    private String email() {
        return "sim-" + tag + "@example.com";
    }

    /**
     * 本文を読む。
     *
     * <p><b>読めなければ空として扱う。</b> 断られた応答が JSON とは限らず、
     * ここで例外にすると「どう断られたか」が失われる。</p>
     */
    private JsonNode parse(GatewayCalls.Response response) {
        try {
            return json.readTree(response.body() == null ? "{}" : response.body());
        } catch (java.io.IOException e) {
            return json.createObjectNode();
        }
    }
}
