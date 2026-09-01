package com.example.simulationms.infrastructure.acl;

import com.example.simulationms.application.internal.outboundservices.acl.BusinessCallFailedException;
import com.example.simulationms.application.internal.outboundservices.acl.BusinessGateway;
import com.example.simulationms.domain.model.valueobjects.BusinessContextKey;
import com.example.simulationms.domain.model.valueobjects.ScenarioStep;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 業務 API を Gateway 経由で踏む出口（[ADR-030] 決定 2）。
 *
 * <p><strong>工程ごとにログインし直す。</strong>切符を使い回すと、1 つの利用者に
 * 全ロールを与えたのと同じ状態になる——本番には存在しない権限の持ち主が生まれる。
 * ログインの往復は増えるが、確かめたいのは「そのロールでその操作が通るか」である。
 *
 * <p><strong>経路は設定にしない。</strong>相手との契約であり、環境ごとに変わるのは
 * 所在（ベース URL）だけである。
 */
@SuppressWarnings("java:S1075")
public class RestBusinessGateway implements BusinessGateway {

    /** ログインの経路。Gateway が認証不要で通す唯一の POST である。 */
    public static final String LOGIN_PATH = "/api/v1/auth/login";

    public static final String SHIPPER_PATH = "/api/v1/shippers";
    public static final String BOOKING_PATH = "/api/v1/bookings";
    public static final String VOYAGE_PATH = "/api/v1/voyages";
    public static final String ROUTE_PATH = "/api/v1/routes";
    public static final String HANDLING_PATH = "/api/v1/handling";
    public static final String CUSTOMS_PATH = "/api/v1/customs";
    public static final String TRACKING_MANAGE_PATH = "/api/v1/tracking/manage";
    public static final String BILLING_PATH = "/api/v1/billing";

    /** 通す輸送。**固定する**——毎回変えると、失敗したときに条件の違いを疑うことになる。 */
    static final String ORIGIN = "JPTYO";
    static final String DESTINATION = "USLAX";
    static final String CARGO_TYPE = "GENERAL";

    /** 業務データに残す名乗り。**誰の操作か分かる名前にする**——後から人が見分けられる。 */
    static final String OPERATOR = "シミュレーション";

    /** 到着期限までの日数。航海の到着（20 日後）より十分に後へ置く。 */
    private static final int DEADLINE_DAYS = 120;

    private final RestClient gateway;
    private final SimulationUsers users;
    private final Clock clock;

    /**
     * 例外を起こす・対応する工程の出口。
     *
     * <p>分けたのは行数の都合ではなく<strong>変わる理由が違う</strong>ためである。
     * 出口そのものは増えていない——[ADR-030] 決定 2 の「1 ポート」は
     * {@code BusinessGateway} の話であり、その内側の分け方は別である。
     */
    private final RestExceptionSteps exceptions;

    public RestBusinessGateway(RestClient gateway, SimulationUsers users, Clock clock) {
        this.gateway = gateway;
        this.users = users;
        this.clock = clock;
        this.exceptions = new RestExceptionSteps(gateway, clock);
    }

    @Override
    public String execute(ScenarioStep step, Map<String, String> context) {
        String token = login(step.role());

        // **既定の分岐を置かない。**置くと、工程を足したときに何も呼ばないまま
        // 「成功」で通り抜ける。列挙を網羅させ、足した工程が必ずここへ現れるようにする
        return switch (step) {
            case REGISTER_SHIPPER -> registerShipper(token, context);
            case REGISTER_BOOKING -> registerBooking(token, context);
            case REQUEST_ROUTING -> post(step, token,
                    bookingPath(context, "/routing-request"));
            case REGISTER_VOYAGE -> registerVoyage(token, context);
            case ASSIGN_ROUTE -> assignRoute(token, context);
            case NOTIFY_ROUTE -> post(step, token,
                    bookingPath(context, "/route-notification"));
            case CONFIRM_BOOKING -> put(step, token, bookingPath(context, "/confirm"));
            case ISSUE_TRACKING_NUMBER -> issueTrackingNumber(token, context);
            case RECORD_HANDLING -> recordHandling(token, context);
            case DECLARE_CUSTOMS -> declareCustoms(token, context);
            case CLEAR_CUSTOMS -> clearCustoms(token, context);
            case RECORD_CLAIM -> recordClaim(token, context);
            case CALCULATE_CHARGE -> calculateCharge(token, context);
            case SETTLE -> settle(token, context);
            case RECORD_LATE_HANDLING, RAISE_DAMAGE, RECORD_MISROUTED_HANDLING, HOLD_CUSTOMS,
                    REQUEST_CANCELLATION, RESOLVE_EXCEPTION, REGISTER_RECOVERY_VOYAGE,
                    REDESIGN_ROUTE, RELEASE_CUSTOMS, APPROVE_CANCELLATION ->
                    exceptions.execute(step, token, context);
        };
    }

    /**
     * その工程を踏むロールの利用者として入る。
     *
     * <p>失敗したら<strong>誰として入ろうとしたか</strong>を添える。
     * 「ログインに失敗しました」だけでは、名簿の設定が違うのか利用者が消えているのかを
     * 切り分けられない。
     */
    private String login(String role) {
        String username = users.usernameFor(role);
        try {
            BusinessMessages.LoginResponse response = gateway.post()
                    .uri(LOGIN_PATH)
                    .body(new BusinessMessages.LoginRequest(username, users.password()))
                    .retrieve()
                    .body(BusinessMessages.LoginResponse.class);

            if (response == null || response.token() == null || response.token().isBlank()) {
                throw new BusinessCallFailedException(
                        "ログインの応答に切符がありません: " + username);
            }
            return response.token();
        } catch (RestClientException e) {
            throw new BusinessCallFailedException(
                    "ログインできません: " + username + "（" + BusinessCalls.describe(e) + "）", e);
        }
    }

    private String registerShipper(String token, Map<String, String> context) {
        String marker = BusinessCalls.runId(context);
        BusinessMessages.ShipperResponse response = BusinessCalls.call(ScenarioStep.REGISTER_SHIPPER, () -> gateway.post()
                .uri(SHIPPER_PATH)
                .header(HttpHeaders.AUTHORIZATION, BusinessCalls.bearer(token))
                .body(new BusinessMessages.ShipperRequest(
                        // **個人にする。**法人は契約番号が要り、無いと集約が断る——
                        // 確かめたいのは業務の道のりであって、契約の妥当性ではない
                        "INDIVIDUAL",
                        OPERATOR + "荷主 " + marker,
                        marker.toLowerCase(Locale.ROOT) + "@simulation.example.com",
                        "東京都千代田区 1-1-1",
                        "03-0000-0000",
                        true,
                        // **シミュレーション由来として登録する**（[ADR-030] 決定 3）。
                        // 送り忘れると、実データに混ざったまま経理の締めに乗る
                        true))
                .retrieve()
                .body(BusinessMessages.ShipperResponse.class));

        if (response == null || response.id() == null) {
            throw new BusinessCallFailedException("荷主登録の応答に荷主がありません");
        }
        return String.valueOf(response.id());
    }

    private String registerBooking(String token, Map<String, String> context) {
        BusinessMessages.BookingResponse response = BusinessCalls.call(ScenarioStep.REGISTER_BOOKING, () -> gateway.post()
                .uri(BOOKING_PATH)
                .header(HttpHeaders.AUTHORIZATION, BusinessCalls.bearer(token))
                .body(new BusinessMessages.BookingRequest(
                        Long.valueOf(BusinessCalls.required(context, BusinessContextKey.SHIPPER_ID)),
                        CARGO_TYPE, 900, OPERATOR + " " + BusinessCalls.runId(context),
                        ORIGIN, DESTINATION, today().plusDays(DEADLINE_DAYS).toString()))
                .retrieve()
                .body(BusinessMessages.BookingResponse.class));

        if (response == null || response.bookingId() == null) {
            throw new BusinessCallFailedException("予約登録の応答に予約番号がありません");
        }
        return response.bookingId();
    }

    /**
     * 航海を登録する。
     *
     * <p><strong>まっさらな環境では航海が 1 本も無い。</strong>「候補が無いので飛ばす」に
     * すると、何も運ばないまま全工程が成功で終わる（IT5 の Try 2）。
     */
    private String registerVoyage(String token, Map<String, String> context) {
        String number = voyageNumberOf(BusinessCalls.runId(context));
        Instant departure = clock.instant().plus(1, ChronoUnit.DAYS);
        Instant arrival = clock.instant().plus(20, ChronoUnit.DAYS);

        BusinessCalls.call(ScenarioStep.REGISTER_VOYAGE, () -> gateway.post()
                .uri(VOYAGE_PATH)
                .header(HttpHeaders.AUTHORIZATION, BusinessCalls.bearer(token))
                .body(new BusinessMessages.VoyageRequest(number, "シミュレーション丸", "シミュレーション海運",
                        List.of(CARGO_TYPE),
                        List.of(new BusinessMessages.VoyageRequest.MovementRequest(
                                ORIGIN, DESTINATION, departure, arrival))))
                .retrieve()
                .toBodilessEntity());
        return number;
    }

    /** シミュレーションが登録した航海と分かる名前にする（[ADR-030] 決定 3）。 */
    static String voyageNumberOf(String runId) {
        return "V-" + runId;
    }

    private String assignRoute(String token, Map<String, String> context) {
        String deadline = today().plusDays(DEADLINE_DAYS).toString();
        BusinessMessages.RouteCandidateListResponse candidates = BusinessCalls.call(ScenarioStep.ASSIGN_ROUTE, () -> gateway.get()
                .uri(UriComponentsBuilder.fromPath(ROUTE_PATH)
                        .queryParam("origin", ORIGIN)
                        .queryParam("destination", DESTINATION)
                        .queryParam("deadline", deadline)
                        .queryParam("cargoType", CARGO_TYPE)
                        .toUriString())
                .header(HttpHeaders.AUTHORIZATION, BusinessCalls.bearer(token))
                .retrieve()
                .body(BusinessMessages.RouteCandidateListResponse.class));

        if (candidates == null || candidates.candidates() == null
                || candidates.candidates().isEmpty()) {
            throw new BusinessCallFailedException(
                    "経路候補が 0 件です（" + ORIGIN + " → " + DESTINATION
                            + "・期限 " + deadline + "）。航海の登録を確かめる");
        }

        // **自分が登録した航海を通る候補を選ぶ。**先頭を無条件に採ると、
        // 別の実行が登録した航海に割り当ててしまう。荷役はこの実行の航海番号で
        // 記録するため、経路と荷役の航海が食い違い、誤配として起票される。
        // 1 件ずつ手で流していたあいだは候補が 1 つしか無く、表面化しなかった。
        String ownVoyage = BusinessCalls.required(context, BusinessContextKey.VOYAGE_NUMBER);
        BusinessMessages.RouteCandidateListResponse.Candidate chosen =
                candidates.candidates().stream()
                        .filter(candidate -> candidate.legs() != null
                                && candidate.legs().stream()
                                        .allMatch(leg -> ownVoyage.equals(leg.voyageNumber())))
                        .findFirst()
                        .orElseThrow(() -> new BusinessCallFailedException(
                                "自分が登録した航海（" + ownVoyage + "）を通る経路候補がありません。"
                                        + "候補は " + candidates.candidates().size() + " 件ありました"));

        List<BusinessMessages.LegRequest> legs = chosen.legs().stream()
                .map(leg -> new BusinessMessages.LegRequest(leg.voyageNumber(), leg.fromUnLocode(),
                        leg.toUnLocode(), leg.departureTime(), leg.arrivalTime()))
                .toList();

        BusinessCalls.call(ScenarioStep.ASSIGN_ROUTE, () -> gateway.put()
                .uri(bookingPath(context, "/route"))
                .header(HttpHeaders.AUTHORIZATION, BusinessCalls.bearer(token))
                .body(new BusinessMessages.AssignRouteRequest(legs, null))
                .retrieve()
                .toBodilessEntity());
        return BusinessContextKey.NONE;
    }

    private String issueTrackingNumber(String token, Map<String, String> context) {
        BusinessMessages.BookingResponse response = BusinessCalls.call(ScenarioStep.ISSUE_TRACKING_NUMBER, () -> gateway.post()
                .uri(bookingPath(context, "/tracking-number"))
                .header(HttpHeaders.AUTHORIZATION, BusinessCalls.bearer(token))
                .retrieve()
                .body(BusinessMessages.BookingResponse.class));

        if (response == null || response.trackingNumber() == null) {
            throw new BusinessCallFailedException("追跡番号発行の応答に追跡番号がありません");
        }
        return response.trackingNumber();
    }


    /**
     * 受け取り・積込・荷降しを記録する。
     *
     * <p><strong>3 つとも踏む。</strong>引取は荷降しの後にしか成り立たず、途中を飛ばすと
     * 「引取が断られる」形でしか気づけない——原因は飛ばしたこちらにある。
     */
    private String recordHandling(String token, Map<String, String> context) {
        String trackingNumber = BusinessCalls.required(context, BusinessContextKey.TRACKING_NUMBER);
        String voyageNumber = BusinessCalls.required(context, BusinessContextKey.VOYAGE_NUMBER);
        Instant at = clock.instant();

        record Activity(String type, String location, String voyage, Instant at) {
        }

        List<Activity> activities = List.of(
                new Activity("RECEIVE", ORIGIN, null, at),
                new Activity("LOAD", ORIGIN, voyageNumber, at.plus(1, ChronoUnit.HOURS)),
                new Activity("UNLOAD", DESTINATION, voyageNumber, at.plus(2, ChronoUnit.HOURS)));

        for (Activity activity : activities) {
            BusinessCalls.call(ScenarioStep.RECORD_HANDLING, () -> gateway.post()
                    .uri(HANDLING_PATH)
                    .header(HttpHeaders.AUTHORIZATION, BusinessCalls.bearer(token))
                    .body(new BusinessMessages.HandlingActivityRequest(trackingNumber, activity.type(),
                            activity.location(), activity.at().toString(),
                            OPERATOR, activity.voyage(), null))
                    .retrieve()
                    .toBodilessEntity());
        }
        return BusinessContextKey.NONE;
    }

    private String declareCustoms(String token, Map<String, String> context) {
        BusinessMessages.CustomsDeclarationResponse response = BusinessCalls.call(ScenarioStep.DECLARE_CUSTOMS,
                () -> gateway.post()
                        .uri(CUSTOMS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, BusinessCalls.bearer(token))
                        .body(new BusinessMessages.CustomsDeclarationRequest(
                                BusinessCalls.required(context, BusinessContextKey.TRACKING_NUMBER),
                                "DEC-" + BusinessCalls.runId(context),
                                clock.instant().toString(),
                                OPERATOR))
                        .retrieve()
                        .body(BusinessMessages.CustomsDeclarationResponse.class));

        if (response == null || response.declarationId() == null) {
            throw new BusinessCallFailedException("通関申告の応答に申告がありません");
        }
        return String.valueOf(response.declarationId());
    }

    private String clearCustoms(String token, Map<String, String> context) {
        BusinessCalls.call(ScenarioStep.CLEAR_CUSTOMS, () -> gateway.put()
                .uri(CUSTOMS_PATH + "/" + BusinessCalls.required(context, BusinessContextKey.DECLARATION_ID)
                        + "/status")
                .header(HttpHeaders.AUTHORIZATION, BusinessCalls.bearer(token))
                .body(new BusinessMessages.CustomsStatusRequest("CLEARED", "シミュレーションの審査完了"))
                .retrieve()
                .toBodilessEntity());
        return BusinessContextKey.NONE;
    }

    /**
     * 引取を記録する。
     *
     * <p><strong>荷受人の確認を添える。</strong>添えないと集約が断る（US16-2）——
     * 断られること自体は正しい振る舞いであり、こちらの入力が足りていない。
     */
    private String recordClaim(String token, Map<String, String> context) {
        BusinessCalls.call(ScenarioStep.RECORD_CLAIM, () -> gateway.post()
                .uri(HANDLING_PATH)
                .header(HttpHeaders.AUTHORIZATION, BusinessCalls.bearer(token))
                .body(new BusinessMessages.HandlingActivityRequest(
                        BusinessCalls.required(context, BusinessContextKey.TRACKING_NUMBER), "CLAIM",
                        DESTINATION, clock.instant().plus(3, ChronoUnit.HOURS).toString(),
                        OPERATOR, null, "シミュレーション荷受人"))
                .retrieve()
                .toBodilessEntity());
        return BusinessContextKey.NONE;
    }

    private String calculateCharge(String token, Map<String, String> context) {
        String bookingId = BusinessCalls.required(context, BusinessContextKey.BOOKING_ID);
        BusinessMessages.InvoiceResponse invoice = BusinessCalls.call(ScenarioStep.CALCULATE_CHARGE, () -> gateway.post()
                .uri(BILLING_PATH + "/" + bookingId + "/calculate")
                .header(HttpHeaders.AUTHORIZATION, BusinessCalls.bearer(token))
                .body(new BusinessMessages.CalculateRequest(List.of()))
                .retrieve()
                .body(BusinessMessages.InvoiceResponse.class));

        if (invoice == null || invoice.invoiceNumber() == null) {
            throw new BusinessCallFailedException("料金算出の応答に精算書がありません");
        }
        return invoice.invoiceNumber();
    }

    /**
     * 入金を記録して精算を終える。
     *
     * <p><strong>金額は精算書から読む。</strong>こちらで計算し直すと、料金の式が 2 つに増える。
     */
    private String settle(String token, Map<String, String> context) {
        String invoiceNumber = BusinessCalls.required(context, BusinessContextKey.INVOICE_NUMBER);
        BusinessMessages.InvoiceResponse invoice = BusinessCalls.call(ScenarioStep.SETTLE, () -> gateway.get()
                .uri(BILLING_PATH + "/invoices/" + invoiceNumber)
                .header(HttpHeaders.AUTHORIZATION, BusinessCalls.bearer(token))
                .retrieve()
                .body(BusinessMessages.InvoiceResponse.class));

        if (invoice == null || invoice.totalAmount() == null
                || invoice.totalAmount().value() == null) {
            throw new BusinessCallFailedException("精算書に請求金額がありません: " + invoiceNumber);
        }

        BusinessCalls.call(ScenarioStep.SETTLE, () -> gateway.post()
                .uri(BILLING_PATH + "/invoices/" + invoiceNumber + "/payment")
                .header(HttpHeaders.AUTHORIZATION, BusinessCalls.bearer(token))
                .body(new BusinessMessages.ConfirmPaymentRequest(invoice.totalAmount().value(), today().toString(),
                        "BANK_TRANSFER", "SIM-" + BusinessCalls.runId(context)))
                .retrieve()
                .toBodilessEntity());
        return BusinessContextKey.NONE;
    }

    private String post(ScenarioStep step, String token, String path) {
        BusinessCalls.call(step, () -> gateway.post()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, BusinessCalls.bearer(token))
                .retrieve()
                .toBodilessEntity());
        return BusinessContextKey.NONE;
    }

    private String put(ScenarioStep step, String token, String path) {
        BusinessCalls.call(step, () -> gateway.put()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, BusinessCalls.bearer(token))
                .retrieve()
                .toBodilessEntity());
        return BusinessContextKey.NONE;
    }

    private String bookingPath(Map<String, String> context, String suffix) {
        return BOOKING_PATH + "/" + BusinessCalls.required(context, BusinessContextKey.BOOKING_ID) + suffix;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

}
