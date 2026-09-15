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
        var api = start(Scenario.CUSTOMS_HOLD);

        var result = api.execute(StepKind.REGISTER_EXCEPTION, inTransit());

        assertThat(result.producedId()).isEqualTo(EXCEPTION);
        assertThat(bodies)
                .as("**種類は入力で変える**（注 N10）")
                .anyMatch(body -> body.contains("CUSTOMS_HOLD"));
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
                "{\"candidates\":[{\"unLocode\":\"SGSIN\"}]}");
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
                "{\"candidates\":[]}");
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
}
