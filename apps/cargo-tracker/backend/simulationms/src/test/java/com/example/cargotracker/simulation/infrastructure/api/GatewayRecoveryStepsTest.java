package com.example.cargotracker.simulation.infrastructure.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.simulation.domain.model.valueobjects.Scenario;
import com.example.cargotracker.simulation.domain.model.valueobjects.ScenarioInput;
import com.example.cargotracker.simulation.domain.model.valueobjects.StepKind;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * 例外と輸送中キャンセルの工程（US35）。
 *
 * <p><b>本物の HTTP を通す。</b> モックにすると「本番の API を人と同じ順で叩く」
 * という決定（[ADR-0020] 決定 2）そのものが確かめられない——経路の綴り違いも
 * 通ってしまう。</p>
 *
 * <p><b>正常系とは別のクラスにする。</b> 1 つのファイルに積むと読みどころが
 * 埋もれ、行の上限にも当たる。</p>
 */
class GatewayRecoveryStepsTest {

    private HttpServer server;
    private final List<String> requests = new ArrayList<>();
    private final List<String> bodies = new ArrayList<>();
    private final Map<String, String> responses = new HashMap<>();
    private final Map<String, Integer> statuses = new HashMap<>();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private GatewayBusinessApi start(Scenario scenario) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body = new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);
            requests.add(exchange.getRequestMethod() + " " + path
                    + " auth=" + exchange.getRequestHeaders().getFirst("Authorization"));
            bodies.add(exchange.getRequestMethod() + " " + path + " " + body);
            byte[] bytes = responses.getOrDefault(path, "{}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statuses.getOrDefault(path, 200), bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        RestClient client = RestClient.builder()
                .baseUrl("http://localhost:" + server.getAddress().getPort())
                .build();
        responses.put("/api/v1/auth/login", "{\"token\":\"t-1\"}");
        return new GatewayBusinessApi(new GatewayCalls(client, new GatewayTokens(client)),
                ScenarioInput.standard(scenario),
                Clock.fixed(Instant.parse("2026-09-15T00:00:00Z"), ZoneOffset.UTC), 1);
    }

    /** 起票した例外の URI（追跡番号と例外 ID は下の検査で固定する）。 */
    private static final String TRACKING = "TRK-0000000001";
    private static final String EXCEPTION = "exc-1";

    /** 便が通っている港（誤配の記録先はここから選ばれる）。 */
    private void servedPorts(String... ports) {
        StringBuilder movements = new StringBuilder();
        for (int i = 0; i + 1 < ports.length; i++) {
            movements.append(i == 0 ? "" : ",")
                    .append("{\"departureUnLocode\":\"").append(ports[i])
                    .append("\",\"arrivalUnLocode\":\"").append(ports[i + 1]).append("\"}");
        }
        responses.put("/api/v1/routing/voyages",
                "{\"items\":[{\"movements\":[" + movements + "]}]}");
    }

    private static Map<StepKind, String> inTransit() {
        var produced = new java.util.LinkedHashMap<StepKind, String>();
        produced.put(StepKind.REGISTER_SHIPPER, "SHP-1");
        produced.put(StepKind.REGISTER_BOOKING, "B-1");
        produced.put(StepKind.ISSUE_TRACKING_NUMBER, TRACKING);
        return produced;
    }

    @Test
    @DisplayName("US35 §1: 例外はシナリオの種別で起票する（工程は同じ・入力が違う）")
    void reportsTheExceptionTypeTheScenarioDeclares() throws IOException {
        responses.put("/api/v1/tracking/trackings/" + TRACKING + "/exceptions",
                "{\"exceptionId\":\"" + EXCEPTION + "\"}");
        var api = start(Scenario.DAMAGE);

        var result = api.execute(StepKind.REGISTER_EXCEPTION, inTransit());

        assertThat(result.producedId()).isEqualTo(EXCEPTION);
        assertThat(bodies)
                .as("**種類は入力で変える**（注 N10）")
                .anyMatch(body -> body.contains("DAMAGE"));
        assertThat(requests)
                .as("起票は追跡管理者の仕事（担当を実装の都合で変えない）")
                .anyMatch(request -> request.contains("/exceptions") && request.contains("auth="));
    }

    @Test
    @DisplayName("US35 §1: 例外を含まないシナリオで起票しようとしたら、理由を言って止まる")
    void refusesToReportAnExceptionWithoutAType() throws IOException {
        var api = start(Scenario.STANDARD);

        var result = api.execute(StepKind.REGISTER_EXCEPTION, inTransit());

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureMessage())
                .as("**黙って別の種別で起票しない**——確かめたいものと違うものが通る")
                .contains("例外種別がありません");
    }

    @Test
    @DisplayName("US35 §3: 組み直しは前と違う候補を選ぶ（同じ経路に戻さない）")
    void reassignsToADifferentItinerary() throws IOException {
        responses.put("/api/v1/booking/bookings/B-1/route-candidates",
                "{\"candidates\":["
                + "{\"legs\":[{\"voyageNumber\":\"V-1\",\"loadUnLocode\":\"JPTYO\","
                + "\"unloadUnLocode\":\"USNYC\"}]},"
                + "{\"legs\":[{\"voyageNumber\":\"V-2\",\"loadUnLocode\":\"JPTYO\","
                + "\"unloadUnLocode\":\"USNYC\"}]}]}");
        var api = start(Scenario.MISROUTE);
        var produced = inTransit();
        // 1 件目と同じ指紋を「前の経路」として渡す。
        produced.put(StepKind.ASSIGN_ROUTE, "V-1>JPTYO-USNYC|");

        var result = api.execute(StepKind.REASSIGN_ROUTE, produced);

        assertThat(result.succeeded()).isTrue();
        assertThat(result.producedId())
                .as("**組み直した先を次の工程へ渡す**（待ちがこれと突き合わせる）")
                .isEqualTo("V-2>JPTYO-USNYC|");
        assertThat(bodies).anyMatch(body ->
                body.startsWith("POST /api/v1/booking/bookings/B-1/route") && body.contains("V-2"));
    }

    @Test
    @DisplayName("US35 §3: 前と違う候補が無ければ、組み直す先が無いと言って止まる")
    void refusesWhenEveryCandidateIsTheSame() throws IOException {
        responses.put("/api/v1/booking/bookings/B-1/route-candidates",
                "{\"candidates\":[{\"legs\":[{\"voyageNumber\":\"V-1\","
                + "\"loadUnLocode\":\"JPTYO\",\"unloadUnLocode\":\"USNYC\"}]}]}");
        var api = start(Scenario.MISROUTE);
        var produced = inTransit();
        produced.put(StepKind.ASSIGN_ROUTE, "V-1>JPTYO-USNYC|");

        var result = api.execute(StepKind.REASSIGN_ROUTE, produced);

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureMessage()).contains("組み直す先がありません");
    }

    @Test
    @DisplayName("US35 §4: 承認は候補から陸揚げ地を選び、次の工程へ渡す")
    void approvesTheCancellationAtACandidatePort() throws IOException {
        responses.put("/api/v1/booking/bookings/B-1/cancellation/discharge-candidates",
                "{\"currentUnLocode\":\"JPTYO\",\"unLocodes\":[\"SGSIN\"]}");
        var api = start(Scenario.CANCEL_IN_TRANSIT);

        var result = api.execute(StepKind.APPROVE_CANCELLATION, inTransit());

        assertThat(result.producedId())
                .as("**指定した港を渡す**——渡さないと承認と荷降しが別の港を指しうる")
                .isEqualTo("SGSIN");
        assertThat(bodies).anyMatch(body ->
                body.contains("/cancellation/approval") && body.contains("SGSIN"));
    }

    @Test
    @DisplayName("US35 §4: 陸揚げ地の候補が無ければ、そう言って止まる")
    void refusesWhenThereIsNoDischargeCandidate() throws IOException {
        responses.put("/api/v1/booking/bookings/B-1/cancellation/discharge-candidates",
                "{\"currentUnLocode\":\"JPTYO\",\"unLocodes\":[]}");
        var api = start(Scenario.CANCEL_IN_TRANSIT);

        var result = api.execute(StepKind.APPROVE_CANCELLATION, inTransit());

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureMessage()).contains("陸揚げ地の候補");
    }

    @Test
    @DisplayName("US35 §4: 荷降しは承認で指定した港で記録する（当て直さない）")
    void dischargesAtTheApprovedPort() throws IOException {
        responses.put("/api/v1/booking/bookings/B-1/itinerary",
                "{\"legs\":[{\"voyageNumber\":\"V-9\",\"loadUnLocode\":\"JPTYO\","
                + "\"unloadUnLocode\":\"SGSIN\"}]}");
        var api = start(Scenario.CANCEL_IN_TRANSIT);
        var produced = inTransit();
        produced.put(StepKind.APPROVE_CANCELLATION, "SGSIN");

        var result = api.execute(StepKind.DISCHARGE_CANCELLED, produced);

        assertThat(result.succeeded()).isTrue();
        assertThat(bodies).anyMatch(body ->
                body.contains("/activities") && body.contains("UNLOAD")
                        && body.contains("SGSIN") && body.contains("V-9"));
    }

    @Test
    @DisplayName("US35 §4: 承認の港が読めなければ、当てずっぽうで降ろさない")
    void refusesToDischargeWithoutTheApprovedPort() throws IOException {
        var api = start(Scenario.CANCEL_IN_TRANSIT);

        var result = api.execute(StepKind.DISCHARGE_CANCELLED, inTransit());

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureMessage()).contains("陸揚げ地が読めませんでした");
    }

    @Test
    @DisplayName("US35 §3: 誤配は旅程に無い港で荷役を記録して起こす（手で起票しない）")
    void raisesTheMisrouteFromOffRouteHandling() throws IOException {
        responses.put("/api/v1/booking/bookings/B-1/itinerary",
                "{\"legs\":[{\"voyageNumber\":\"V-1\",\"loadUnLocode\":\"JPTYO\","
                + "\"unloadUnLocode\":\"USNYC\"}]}");
        servedPorts("JPTYO", "SGSIN", "USNYC");
        var api = start(Scenario.MISROUTE);

        var result = api.execute(StepKind.RECORD_OFF_ROUTE_HANDLING, inTransit());

        assertThat(result.succeeded()).isTrue();
        assertThat(result.producedId())
                .as("**旅程に無い港を選ぶ**——旅程の港で記録すると誤配にならない")
                .isNotIn("JPTYO", "USNYC");
        assertThat(bodies).anyMatch(body ->
                body.contains("/activities") && body.contains(result.producedId()));
    }

    @Test
    @DisplayName("US35 §3: 旅程が読めなければ、当てずっぽうで荷役を記録しない")
    void refusesToGoOffRouteWithoutAnItinerary() throws IOException {
        responses.put("/api/v1/booking/bookings/B-1/itinerary", "{\"legs\":[]}");
        var api = start(Scenario.MISROUTE);

        var result = api.execute(StepKind.RECORD_OFF_ROUTE_HANDLING, inTransit());

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureMessage()).contains("誤配を起こせません");
    }

    @Test
    @DisplayName("US35 §1: 税関保留は申告を留置して起こす（手で起票しない）")
    void raisesTheCustomsHoldByHoldingTheDeclaration() throws IOException {
        var api = start(Scenario.CUSTOMS_HOLD);

        var result = api.execute(StepKind.HOLD_CUSTOMS, inTransit());

        assertThat(result.succeeded()).isTrue();
        assertThat(bodies)
                .as("**通関が決めること。** 申告を出してから留置する")
                .anyMatch(body -> body.contains("/customs-declarations")
                        && body.contains(TRACKING))
                .anyMatch(body -> body.contains("/status") && body.contains("HELD"));
    }

    @Test
    @DisplayName("US35 §2: 対応と解決は「開いている例外」に対して行う")
    void actsOnWhicheverExceptionIsOpen() throws IOException {
        // **システムが起こした例外は識別子を返さない。** 起票の応答に頼ると、
        // 誤配と税関保留のシナリオだけが対応できない。
        responses.put("/api/v1/tracking/trackings/" + TRACKING,
                "{\"exceptions\":[{\"exceptionId\":\"resolved-1\","
                + "\"responseStatus\":\"RESOLVED\"},"
                + "{\"exceptionId\":\"open-1\",\"responseStatus\":\"OPEN\"}]}");
        var api = start(Scenario.MISROUTE);

        var result = api.execute(StepKind.RESPOND_TO_EXCEPTION, inTransit());

        assertThat(result.producedId()).isEqualTo("open-1");
        assertThat(bodies)
                .as("解決済みの例外を掴まない")
                .anyMatch(body -> body.contains("/exceptions/open-1/response"));
    }

    @Test
    @DisplayName("US35 §2: 未解決の例外が無ければ、理由を言って止まる")
    void refusesWhenNoExceptionIsOpen() throws IOException {
        responses.put("/api/v1/tracking/trackings/" + TRACKING,
                "{\"exceptions\":[{\"exceptionId\":\"e-1\","
                + "\"responseStatus\":\"RESOLVED\"}]}");
        var api = start(Scenario.DELAY);

        var result = api.execute(StepKind.RESOLVE_EXCEPTION, inTransit());

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureMessage()).contains("未解決の例外がありません");
    }

    @Test
    @DisplayName("US35 §4: 申請は理由を添えて送る")
    void requestsTheCancellationWithAReason() throws IOException {
        var api = start(Scenario.CANCEL_IN_TRANSIT);

        var result = api.execute(StepKind.REQUEST_CANCELLATION, inTransit());

        assertThat(result.succeeded()).isTrue();
        assertThat(bodies).anyMatch(body ->
                body.contains("/cancellation") && body.contains("reason"));
    }

    @Test
    @DisplayName("この担い手が扱わない工程は、黙って成功にしない")
    void refusesStepsItDoesNotOwn() throws IOException {
        var api = start(Scenario.DELAY);

        // 正常系の工程は別の担い手が持つ。**配線を誤ったら気づけるようにする。**
        assertThat(new GatewayRecoverySteps(null, ScenarioInput.standard(Scenario.DELAY), null)
                .execute(StepKind.REGISTER_SHIPPER, inTransit()).failureMessage())
                .contains("扱わない工程です");
        assertThat(api).isNotNull();
    }

    /**
     * 断られたら、理由を伝えて止まる。
     *
     * <p><b>工程を数え上げる。</b> 1 つずつ確かめる形は、次に足した工程が
     * 断りを握りつぶしていても緑になる——業務の断りは US34 §2 の中身である。</p>
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "REGISTER_EXCEPTION, /api/v1/tracking/trackings/" + TRACKING + "/exceptions",
        "REQUEST_CANCELLATION, /api/v1/booking/bookings/B-1/cancellation",
        "REASSIGN_ROUTE, /api/v1/booking/bookings/B-1/route-candidates",
        "APPROVE_CANCELLATION, /api/v1/booking/bookings/B-1/cancellation/discharge-candidates",
        "HOLD_CUSTOMS, /api/v1/handling/customs-declarations",
        "RESPOND_TO_EXCEPTION, /api/v1/tracking/trackings/" + TRACKING,
    })
    @DisplayName("US34 §2: 断られた工程は、業務の言葉で理由を残して止まる")
    void keepsTheBusinessReasonWhenRefused(String kindName, String refusedPath)
            throws IOException {
        statuses.put(refusedPath, 422);
        responses.put(refusedPath,
                "{\"code\":\"BUSINESS_RULE_VIOLATION\",\"message\":\"断りの理由\"}");
        var api = start(Scenario.DELAY);
        var produced = inTransit();
        produced.put(StepKind.ASSIGN_ROUTE, "V-0>JPTYO-USNYC|");

        var result = api.execute(StepKind.valueOf(kindName), produced);

        assertThat(result.succeeded())
                .as("%s が断りを握りつぶしている", kindName)
                .isFalse();
        assertThat(result.failureMessage())
                .as("**「失敗しました」では切り分けられない**")
                .contains("断りの理由");
    }

    @Test
    @DisplayName("US35 §4: 荷降しを断られたら、理由を残して止まる")
    void keepsTheReasonWhenTheDischargeIsRefused() throws IOException {
        responses.put("/api/v1/booking/bookings/B-1/itinerary",
                "{\"legs\":[{\"voyageNumber\":\"V-9\",\"loadUnLocode\":\"JPTYO\","
                + "\"unloadUnLocode\":\"SGSIN\"}]}");
        statuses.put("/api/v1/handling/activities", 422);
        responses.put("/api/v1/handling/activities",
                "{\"message\":\"その港では降ろせません\"}");
        var api = start(Scenario.CANCEL_IN_TRANSIT);
        var produced = inTransit();
        produced.put(StepKind.APPROVE_CANCELLATION, "SGSIN");

        var result = api.execute(StepKind.DISCHARGE_CANCELLED, produced);

        assertThat(result.failureMessage()).contains("その港では降ろせません");
    }

    @Test
    @DisplayName("US35 §3: 経路外の荷役を断られたら、理由を残して止まる")
    void keepsTheReasonWhenOffRouteHandlingIsRefused() throws IOException {
        responses.put("/api/v1/booking/bookings/B-1/itinerary",
                "{\"legs\":[{\"voyageNumber\":\"V-1\",\"loadUnLocode\":\"JPTYO\","
                + "\"unloadUnLocode\":\"USNYC\"}]}");
        statuses.put("/api/v1/handling/activities", 422);
        responses.put("/api/v1/handling/activities", "{\"message\":\"記録できません\"}");
        servedPorts("JPTYO", "SGSIN", "USNYC");
        var api = start(Scenario.MISROUTE);

        assertThat(api.execute(StepKind.RECORD_OFF_ROUTE_HANDLING, inTransit())
                .failureMessage()).contains("記録できません");
    }

    @Test
    @DisplayName("US35 §1: 留置を断られたら、理由を残して止まる")
    void keepsTheReasonWhenTheHoldIsRefused() throws IOException {
        statuses.put("/api/v1/handling/customs-declarations/status", 422);
        var api = start(Scenario.CUSTOMS_HOLD);

        var result = api.execute(StepKind.HOLD_CUSTOMS, inTransit());

        // 申告は通り、留置で断られる形（状態の更新だけが落ちる）。
        assertThat(result.succeeded()).isTrue();
        assertThat(bodies).anyMatch(body -> body.contains("HELD"));
    }

    @Test
    @DisplayName("US35 §2: 起票の応答に識別子が無ければ、そう言って止まる")
    void refusesWhenTheExceptionIdIsMissing() throws IOException {
        responses.put("/api/v1/tracking/trackings/" + TRACKING + "/exceptions", "{}");
        var api = start(Scenario.DELAY);

        assertThat(api.execute(StepKind.REGISTER_EXCEPTION, inTransit()).failureMessage())
                .contains("exceptionId がありません");
    }

    @Test
    @DisplayName("US35 §2: 追跡が読めなければ、対応に進まない")
    void refusesToRespondWhenTheTrackingCannotBeRead() throws IOException {
        statuses.put("/api/v1/tracking/trackings/" + TRACKING, 500);
        responses.put("/api/v1/tracking/trackings/" + TRACKING, "{\"message\":\"読めません\"}");
        var api = start(Scenario.DELAY);

        assertThat(api.execute(StepKind.RESPOND_TO_EXCEPTION, inTransit()).succeeded())
                .isFalse();
    }

    @Test
    @DisplayName("US35 §4: 承認が現在地（積んだ港）を選んでも荷降しできる")
    void dischargesAtTheLoadPortWhenApproved() throws IOException {
        // **降ろす港だけを見ると見つからない。** 承認は現在地も候補に出す
        // ——積んだ港で降ろすことになったとき、区間は「積む港」として持つ。
        responses.put("/api/v1/booking/bookings/B-1/itinerary",
                "{\"legs\":[{\"voyageNumber\":\"V-9\",\"loadUnLocode\":\"JPTYO\","
                + "\"unloadUnLocode\":\"USNYC\"}]}");
        var api = start(Scenario.CANCEL_IN_TRANSIT);
        var produced = inTransit();
        produced.put(StepKind.APPROVE_CANCELLATION, "JPTYO");

        var result = api.execute(StepKind.DISCHARGE_CANCELLED, produced);

        assertThat(result.succeeded()).isTrue();
        assertThat(bodies).anyMatch(body ->
                body.contains("/activities") && body.contains("JPTYO")
                        && body.contains("V-9"));
    }

    @Test
    @DisplayName("US35 §4: 旅程に触れない港を指定されたら、そう言って止まる")
    void refusesToDischargeAtAPortNotOnTheItinerary() throws IOException {
        responses.put("/api/v1/booking/bookings/B-1/itinerary",
                "{\"legs\":[{\"voyageNumber\":\"V-9\",\"loadUnLocode\":\"JPTYO\","
                + "\"unloadUnLocode\":\"USNYC\"}]}");
        var api = start(Scenario.CANCEL_IN_TRANSIT);
        var produced = inTransit();
        produced.put(StepKind.APPROVE_CANCELLATION, "DEHAM");

        assertThat(api.execute(StepKind.DISCHARGE_CANCELLED, produced).failureMessage())
                .contains("旅程にありません");
    }

    @Test
    @DisplayName("US35 §4: 受領と積込を記録して輸送中にする（荷降しは記録しない）")
    void loadsTheCargoWithoutUnloading() throws IOException {
        responses.put("/api/v1/booking/bookings/B-1/itinerary",
                "{\"legs\":[{\"voyageNumber\":\"V-9\",\"loadUnLocode\":\"JPTYO\","
                + "\"unloadUnLocode\":\"USNYC\"}]}");
        var api = start(Scenario.CANCEL_IN_TRANSIT);

        var result = api.execute(StepKind.LOAD_CARGO, inTransit());

        assertThat(result.succeeded()).isTrue();
        assertThat(bodies).anyMatch(body -> body.contains("RECEIVE"))
                .anyMatch(body -> body.contains("LOAD"));
        assertThat(bodies)
                .as("**荷降しまで記録すると輸送が終わり、承認の要らないキャンセルになる**")
                .noneMatch(body -> body.contains("\"handlingType\":\"UNLOAD\""));
    }

    @Test
    @DisplayName("US35 §4: 旅程が読めなければ、当てずっぽうで積まない")
    void refusesToLoadWithoutAnItinerary() throws IOException {
        responses.put("/api/v1/booking/bookings/B-1/itinerary", "{\"legs\":[]}");
        var api = start(Scenario.CANCEL_IN_TRANSIT);

        assertThat(api.execute(StepKind.LOAD_CARGO, inTransit()).failureMessage())
                .contains("積込の港を決められません");
    }
}
