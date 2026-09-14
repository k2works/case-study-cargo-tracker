package com.example.cargotracker.simulation.infrastructure.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.simulation.application.BusinessApi;
import com.example.cargotracker.simulation.domain.model.valueobjects.Scenario;
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
 * 工程が Gateway のどの経路を叩くか（[ADR-0020] 決定 2）。
 *
 * <p><b>本物の HTTP を通す。</b> モックにすると、認可ヘッダの付け忘れも
 * 経路の綴り違いも通ってしまう。相手は {@code HttpServer} の小さなスタブ。</p>
 */
class GatewayBusinessApiTest {

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
        // **読み直す回数は 1 回にする。** 本物は 60 回（30 秒）待つが、
        // 検査で待つと誰も回さなくなる。
        return new GatewayBusinessApi(new GatewayCalls(client, new GatewayTokens(client)),
                scenario, Clock.fixed(Instant.parse("2026-09-14T00:00:00Z"), ZoneOffset.UTC), 1);
    }

    @Test
    @DisplayName("荷主の登録はシミュレーション由来の印を付けて送る")
    void marksShipperAsSimulated() throws IOException {
        GatewayBusinessApi api = start(Scenario.STANDARD);
        responses.put("/api/v1/booking/shippers", "{\"shipperId\":\"SHP-1\"}");

        BusinessApi.StepResult result = api.execute(StepKind.REGISTER_SHIPPER, Map.of());

        assertThat(result.succeeded()).isTrue();
        assertThat(result.producedId()).isEqualTo("SHP-1");
        // **ログインを踏んで、トークンを添えている。** 内部経路を作っていない証拠。
        assertThat(requests).anyMatch(r -> r.startsWith("POST /api/v1/auth/login"));
        assertThat(requests).anyMatch(r -> r.startsWith("POST /api/v1/booking/shippers")
                && r.contains("auth=Bearer t-1"));
        // **印そのものを見る。** 経路とヘッダだけ見ると、印を落としても緑になる。
        assertThat(bodies).anyMatch(b -> b.startsWith("POST /api/v1/booking/shippers")
                && b.contains("\"simulated\":true"));
    }

    @Test
    @DisplayName("経路候補が 0 件なら「経路が無い」と読める理由で止まる")
    void failsWithoutRouteCandidates() throws IOException {
        GatewayBusinessApi api = start(Scenario.NO_ROUTE);
        responses.put("/api/v1/booking/bookings/BK-1/route-candidates",
                "{\"candidates\":[],\"truncated\":false}");

        BusinessApi.StepResult result = api.execute(StepKind.ASSIGN_ROUTE,
                Map.of(StepKind.REGISTER_BOOKING, "BK-1"));

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureMessage()).contains("経路の候補が 1 件もありません");
        // **確定を叩いていない。** 候補が無いのに送ると、断られ方が変わって
        // 「経路が無い」と読めなくなる。
        assertThat(requests).noneMatch(r -> r.startsWith("POST /api/v1/booking/bookings/BK-1/route "));
    }

    @Test
    @DisplayName("断られた工程は応答コードと本文を持って返る")
    void keepsRefusalDetails() throws IOException {
        GatewayBusinessApi api = start(Scenario.STANDARD);
        statuses.put("/api/v1/booking/bookings/BK-1/confirmation", 422);
        responses.put("/api/v1/booking/bookings/BK-1/confirmation",
                "{\"message\":\"通知していない予約は確定できません\"}");

        BusinessApi.StepResult result = api.execute(StepKind.CONFIRM_BOOKING,
                Map.of(StepKind.REGISTER_BOOKING, "BK-1"));

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureStatus()).isEqualTo(422);
        assertThat(result.failureMessage()).contains("通知していない予約は確定できません");
    }

    @Test
    @DisplayName("荷役は旅程の港と便を使い、受領・積込・荷降しを順に記録する")
    void recordsHandlingAlongTheItinerary() throws IOException {
        GatewayBusinessApi api = start(Scenario.STANDARD);
        responses.put("/api/v1/booking/bookings/BK-1/itinerary", """
                {"legs":[
                  {"voyageNumber":"V001","loadUnLocode":"JPTYO","unloadUnLocode":"USLAX"},
                  {"voyageNumber":"V002","loadUnLocode":"USLAX","unloadUnLocode":"USNYC"}]}
                """);

        BusinessApi.StepResult result = api.execute(StepKind.RECORD_HANDLING, Map.of(
                StepKind.REGISTER_BOOKING, "BK-1",
                StepKind.ISSUE_TRACKING_NUMBER, "TRK-1"));

        assertThat(result.succeeded()).isTrue();
        assertThat(requests.stream()
                .filter(r -> r.startsWith("POST /api/v1/handling/activities"))
                .count()).isEqualTo(3);
    }

    @Test
    @DisplayName("通関は申告を現場が出し、状態は追跡管理者が更新する")
    void clearsCustomsWithTwoRoles() throws IOException {
        GatewayBusinessApi api = start(Scenario.STANDARD);

        BusinessApi.StepResult result = api.execute(StepKind.CLEAR_CUSTOMS,
                Map.of(StepKind.ISSUE_TRACKING_NUMBER, "TRK-1"));

        assertThat(result.succeeded()).isTrue();
        assertThat(result.producedId()).startsWith("SIM-");
        assertThat(requests).anyMatch(
                r -> r.equals("POST /api/v1/handling/customs-declarations auth=Bearer t-1"));
        assertThat(requests).anyMatch(r -> r.startsWith("POST /api/v1/handling/"
                + "customs-declarations/" + result.producedId() + "/status"));
    }

    @Test
    @DisplayName("シナリオで目的地が変わる（経路が組めない条件を構造で作る）")
    void choosesTheDestinationByScenario() throws IOException {
        GatewayBusinessApi standard = start(Scenario.STANDARD);
        responses.put("/api/v1/booking/bookings", "{\"bookingId\":\"BK-1\"}");
        standard.execute(StepKind.REGISTER_BOOKING, Map.of(StepKind.REGISTER_SHIPPER, "SHP-1"));
        String served = bodies.get(bodies.size() - 1);
        stop();
        bodies.clear();

        GatewayBusinessApi noRoute = start(Scenario.NO_ROUTE);
        responses.put("/api/v1/booking/bookings", "{\"bookingId\":\"BK-2\"}");
        noRoute.execute(StepKind.REGISTER_BOOKING, Map.of(StepKind.REGISTER_SHIPPER, "SHP-1"));
        String unserved = bodies.get(bodies.size() - 1);

        assertThat(served).contains("\"destinationUnLocode\":\"USNYC\"");
        // **時間ではなく構造で決める。** 期限の短さで候補を消すと、
        // クラスタに溜まった便次第でたまたま間に合ってしまう（実測）。
        assertThat(unserved).contains("\"destinationUnLocode\":\"AQMCM\"");
        // **期限はどちらも同じだけ先にする。** 期限で断られると、
        // 「経路が無い」ではなく「予約できない」で止まる。
        assertThat(served).contains("2027-01-12");
        assertThat(unserved).contains("2027-01-12");
    }

    @Test
    @DisplayName("追跡番号が読み口に現れなければ、その工程で止まる")
    void failsWhenTrackingNumberIsNotVisibleYet() throws IOException {
        GatewayBusinessApi api = start(Scenario.STANDARD);
        responses.put("/api/v1/booking/bookings/BK-1", "{\"bookingId\":\"BK-1\"}");

        BusinessApi.StepResult result = api.execute(StepKind.ISSUE_TRACKING_NUMBER,
                Map.of(StepKind.REGISTER_BOOKING, "BK-1"));

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureMessage()).contains("読み口に現れませんでした");
    }

    @Test
    @DisplayName("追跡番号は予約の読み口から取る（応答には載らない）")
    void readsTheTrackingNumberFromTheBooking() throws IOException {
        GatewayBusinessApi api = start(Scenario.STANDARD);
        responses.put("/api/v1/booking/bookings/BK-1",
                "{\"bookingId\":\"BK-1\",\"trackingNumber\":\"TRK-1\"}");

        BusinessApi.StepResult result = api.execute(StepKind.ISSUE_TRACKING_NUMBER,
                Map.of(StepKind.REGISTER_BOOKING, "BK-1"));

        assertThat(result.producedId()).isEqualTo("TRK-1");
    }

    @Test
    @DisplayName("請求は連鎖が作ったものを読む（こちらでは作らない）")
    void readsTheInvoiceTheChainProduced() throws IOException {
        GatewayBusinessApi api = start(Scenario.STANDARD);
        responses.put("/api/v1/billing/invoices/by-booking/BK-1",
                "{\"invoiceId\":\"INV-1\"}");

        BusinessApi.StepResult result = api.execute(StepKind.CALCULATE_INVOICE,
                Map.of(StepKind.REGISTER_BOOKING, "BK-1"));

        assertThat(result.producedId()).isEqualTo("INV-1");
        assertThat(requests).noneMatch(r -> r.startsWith("POST /api/v1/billing/invoices"));
    }

    @Test
    @DisplayName("入金は請求の総額で記録する（額を決め打ちにしない）")
    void paysTheInvoicedAmount() throws IOException {
        GatewayBusinessApi api = start(Scenario.STANDARD);
        responses.put("/api/v1/billing/invoices/INV-1",
                "{\"invoiceId\":\"INV-1\",\"totalAmount\":123456.78}");

        BusinessApi.StepResult result = api.execute(StepKind.RECORD_PAYMENT,
                Map.of(StepKind.CALCULATE_INVOICE, "INV-1"));

        assertThat(result.succeeded()).isTrue();
        assertThat(bodies).anyMatch(b -> b.startsWith("POST /api/v1/billing/invoices/INV-1/payments")
                && b.contains("123456.78"));
    }

    @Test
    @DisplayName("請求金額が読めなければ入金しない")
    void doesNotPayWithoutAnAmount() throws IOException {
        GatewayBusinessApi api = start(Scenario.STANDARD);

        BusinessApi.StepResult result = api.execute(StepKind.RECORD_PAYMENT,
                Map.of(StepKind.CALCULATE_INVOICE, "INV-1"));

        assertThat(result.succeeded()).isFalse();
        assertThat(requests).noneMatch(r -> r.contains("/payments"));
    }

    @Test
    @DisplayName("旅程が読めなければ荷役を記録しない（港と便を決められない）")
    void doesNotRecordHandlingWithoutAnItinerary() throws IOException {
        GatewayBusinessApi api = start(Scenario.STANDARD);

        BusinessApi.StepResult result = api.execute(StepKind.RECORD_HANDLING, Map.of(
                StepKind.REGISTER_BOOKING, "BK-1",
                StepKind.ISSUE_TRACKING_NUMBER, "TRK-1"));

        assertThat(result.succeeded()).isFalse();
        assertThat(requests).noneMatch(r -> r.startsWith("POST /api/v1/handling/activities"));
    }

    @Test
    @DisplayName("応答に識別子が無ければ、その工程で止まる")
    void failsWhenTheIdIsMissing() throws IOException {
        GatewayBusinessApi api = start(Scenario.STANDARD);
        responses.put("/api/v1/booking/shippers", "{}");

        BusinessApi.StepResult result = api.execute(StepKind.REGISTER_SHIPPER, Map.of());

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureMessage()).contains("shipperId");
    }

    @Test
    @DisplayName("JSON でない断り方でも理由を失わない")
    void keepsNonJsonRefusals() throws IOException {
        GatewayBusinessApi api = start(Scenario.STANDARD);
        statuses.put("/api/v1/booking/bookings/BK-1/routing-request", 403);
        responses.put("/api/v1/booking/bookings/BK-1/routing-request", "Forbidden");

        BusinessApi.StepResult result = api.execute(StepKind.REQUEST_ROUTING,
                Map.of(StepKind.REGISTER_BOOKING, "BK-1"));

        assertThat(result.failureStatus()).isEqualTo(403);
        assertThat(result.failureMessage()).contains("Forbidden");
    }

    @Test
    @DisplayName("経路の確定は候補の区間をそのまま送る")
    void assignsTheFirstCandidate() throws IOException {
        GatewayBusinessApi api = start(Scenario.STANDARD);
        responses.put("/api/v1/booking/bookings/BK-1/route-candidates", """
                {"candidates":[{"legs":[{"voyageNumber":"V001","loadUnLocode":"JPTYO",
                 "unloadUnLocode":"USNYC","loadTime":"2026-09-20T00:00:00Z",
                 "unloadTime":"2026-10-10T00:00:00Z"}]}]}
                """);

        BusinessApi.StepResult result = api.execute(StepKind.ASSIGN_ROUTE,
                Map.of(StepKind.REGISTER_BOOKING, "BK-1"));

        assertThat(result.succeeded()).isTrue();
        assertThat(bodies).anyMatch(b -> b.startsWith("POST /api/v1/booking/bookings/BK-1/route ")
                && b.contains("V001") && b.contains("2026-10-10T00:00:00Z"));
    }

    @Test
    @DisplayName("通知・確定・請求書の発行・引取も同じ経路を叩く")
    void coversTheRemainingSteps() throws IOException {
        GatewayBusinessApi api = start(Scenario.STANDARD);
        Map<StepKind, String> produced = Map.of(
                StepKind.REGISTER_BOOKING, "BK-1",
                StepKind.ISSUE_TRACKING_NUMBER, "TRK-1",
                StepKind.CALCULATE_INVOICE, "INV-1");
        // 引取の港も旅程から取る（荷役と同じ）。決め打ちにしない。
        responses.put("/api/v1/booking/bookings/BK-1/itinerary",
                "{\"legs\":[{\"voyageNumber\":\"V001\",\"loadUnLocode\":\"JPTYO\","
                        + "\"unloadUnLocode\":\"USNYC\"}]}");

        assertThat(api.execute(StepKind.NOTIFY_SHIPPER, produced).succeeded()).isTrue();
        assertThat(api.execute(StepKind.ISSUE_INVOICE, produced).succeeded()).isTrue();
        assertThat(api.execute(StepKind.CLAIM_CARGO, produced).succeeded()).isTrue();

        assertThat(bodies).anyMatch(b -> b.startsWith("POST /api/v1/booking/bookings/BK-1/notifications")
                && b.contains("@example.com"));
        assertThat(requests).anyMatch(r -> r.startsWith("POST /api/v1/billing/invoices/INV-1/issue"));
        // **引取は荷役の記録として出す**（専用の経路を作らない）。
        assertThat(bodies).anyMatch(b -> b.startsWith("POST /api/v1/handling/activities")
                && b.contains("\"handlingType\":\"CLAIM\"")
                // **旅程の最終区間の港で引き取る**（決め打ちの定数ではない）。
                && b.contains("\"unLocode\":\"USNYC\""));
    }

    @Test
    @DisplayName("断られた工程はどこで断られたかが分かる")
    void namesWhereItWasRefused() throws IOException {
        GatewayBusinessApi api = start(Scenario.STANDARD);
        Map<StepKind, String> produced = Map.of(
                StepKind.REGISTER_BOOKING, "BK-1",
                StepKind.ISSUE_TRACKING_NUMBER, "TRK-1",
                StepKind.CALCULATE_INVOICE, "INV-1");
        statuses.put("/api/v1/handling/activities", 422);
        responses.put("/api/v1/handling/activities", "{\"message\":\"通関が済んでいません\"}");
        responses.put("/api/v1/booking/bookings/BK-1/itinerary",
                "{\"legs\":[{\"voyageNumber\":\"V001\",\"loadUnLocode\":\"JPTYO\","
                        + "\"unloadUnLocode\":\"USNYC\"}]}");
        statuses.put("/api/v1/booking/bookings/BK-1/route-candidates", 500);
        statuses.put("/api/v1/booking/bookings/BK-1/tracking-number", 409);
        statuses.put("/api/v1/handling/customs-declarations", 403);

        assertThat(api.execute(StepKind.CLAIM_CARGO, produced).failureStatus()).isEqualTo(422);
        assertThat(api.execute(StepKind.ASSIGN_ROUTE, produced).failureStatus()).isEqualTo(500);
        assertThat(api.execute(StepKind.ISSUE_TRACKING_NUMBER, produced).failureStatus())
                .isEqualTo(409);
        assertThat(api.execute(StepKind.CLEAR_CUSTOMS, produced).failureStatus()).isEqualTo(403);
        // **どの荷役で止まったかを添える。** 3 件を順に記録するので、
        // 「荷役が断られた」だけでは切り分けられない。
        assertThat(api.execute(StepKind.RECORD_HANDLING, produced).failureMessage())
                .startsWith("RECEIVE:");
    }

    @Test
    @DisplayName("通関は状態の更新で断られてもそこで止まる")
    void stopsWhenCustomsStatusIsRefused() throws IOException {
        GatewayBusinessApi api = start(Scenario.STANDARD);
        server.createContext("/api/v1/handling/customs-declarations/", exchange -> {
            byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(422, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });

        BusinessApi.StepResult result = api.execute(StepKind.CLEAR_CUSTOMS,
                Map.of(StepKind.ISSUE_TRACKING_NUMBER, "TRK-1"));

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureStatus()).isEqualTo(422);
    }

    @Test
    @DisplayName("経路の確定で断られてもそこで止まる")
    void stopsWhenAssigningTheRouteIsRefused() throws IOException {
        GatewayBusinessApi api = start(Scenario.STANDARD);
        responses.put("/api/v1/booking/bookings/BK-1/route-candidates",
                "{\"candidates\":[{\"legs\":[{\"voyageNumber\":\"V001\"}]}]}");
        statuses.put("/api/v1/booking/bookings/BK-1/route", 422);

        BusinessApi.StepResult result = api.execute(StepKind.ASSIGN_ROUTE,
                Map.of(StepKind.REGISTER_BOOKING, "BK-1"));

        assertThat(result.failureStatus()).isEqualTo(422);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        // **実測はこれ**（改行が入る）。版によって残り 2 つの形にもなる。
        "failed.\nCaused by ", "failed. Caused by ", "failed: "})
    @DisplayName("別サービスの断りは、内部の言葉を剥がして人が読む一節だけ出す")
    void unwrapsTheRemoteRefusal(String separator) throws IOException {
        // **経路の問い合わせは別サービスへ渡る。** 断りが Axon の内部文言で
        // 包まれて返るので、そのまま出すと読む人は次の手を決められない
        //（IT16 のクラスタで実測）。
        GatewayBusinessApi api = start(Scenario.NO_ROUTE);
        statuses.put("/api/v1/booking/bookings/BK-1/route-candidates", 422);
        responses.put("/api/v1/booking/bookings/BK-1/route-candidates",
                "{\"code\":\"BUSINESS_RULE_VIOLATION\",\"message\":\"An exception was thrown"
                        + " by the remote message handling component: Handling query with"
                        // **JSON の中では改行を逃がす。** 生の改行を混ぜると
                        // 本文そのものが壊れ、剥がし方ではなく解析の検査になる。
                        + " identifier [6c03cc47] " + separator.replace("\n", "\\n")
                        + "その港を通る航海が登録されていません: AQMCM\"}");

        BusinessApi.StepResult result = api.execute(StepKind.ASSIGN_ROUTE,
                Map.of(StepKind.REGISTER_BOOKING, "BK-1"));

        assertThat(result.failureMessage())
                .isEqualTo("その港を通る航海が登録されていません: AQMCM");
    }
}
