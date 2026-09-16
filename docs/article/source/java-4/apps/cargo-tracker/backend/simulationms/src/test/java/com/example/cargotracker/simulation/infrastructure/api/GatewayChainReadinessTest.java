package com.example.cargotracker.simulation.infrastructure.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.simulation.domain.model.valueobjects.StepKind;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * 連鎖が追いつくまで待つ条件（US33 §受入基準 6）。
 *
 * <p><b>「まだ」と「もう」を言い分けられることを見る。</b> 常に真を返す宣言は、
 * 連鎖が止まっていても次へ進ませ、通っている経路を失敗として記録させる。</p>
 */
class GatewayChainReadinessTest {

    private static final Map<StepKind, String> PRODUCED = Map.of(
            StepKind.REGISTER_SHIPPER, "SHP-1",
            StepKind.REGISTER_BOOKING, "BK-1",
            StepKind.ISSUE_TRACKING_NUMBER, "TRK-1",
            StepKind.CLEAR_CUSTOMS, "DCL-1",
            StepKind.CALCULATE_INVOICE, "INV-1");

    private HttpServer server;
    private final Map<String, String> responses = new HashMap<>();
    private final Map<String, Integer> statuses = new HashMap<>();
    private GatewayChainReadiness readiness;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
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
        readiness = new GatewayChainReadiness(new GatewayCalls(client,
                new GatewayTokens(client)));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    @DisplayName("荷主の投影が現れるまでは進まない（202 は「反映中」）")
    void waitsForTheShipperProjection() {
        statuses.put("/api/v1/booking/shippers/SHP-1", 202);
        assertThat(readiness.isReady(StepKind.REGISTER_SHIPPER, PRODUCED)).isFalse();

        statuses.put("/api/v1/booking/shippers/SHP-1", 200);
        assertThat(readiness.isReady(StepKind.REGISTER_SHIPPER, PRODUCED)).isTrue();
    }

    @Test
    @DisplayName("引き渡し・通知・確定は予約の読み口の値で判別する")
    void readsBookingFields() {
        assertThat(readiness.isReady(StepKind.REQUEST_ROUTING, PRODUCED)).isFalse();
        assertThat(readiness.isReady(StepKind.NOTIFY_SHIPPER, PRODUCED)).isFalse();
        assertThat(readiness.isReady(StepKind.CONFIRM_BOOKING, PRODUCED)).isFalse();

        responses.put("/api/v1/booking/bookings/BK-1", """
                {"bookingId":"BK-1","routingRequestedAt":"2026-09-14T00:00:00Z",
                 "lastNotifiedAt":"2026-09-14T00:00:00Z",
                 "confirmedAt":"2026-09-14T00:00:00Z"}
                """);
        assertThat(readiness.isReady(StepKind.REGISTER_BOOKING, PRODUCED)).isTrue();
        assertThat(readiness.isReady(StepKind.REQUEST_ROUTING, PRODUCED)).isTrue();
        assertThat(readiness.isReady(StepKind.NOTIFY_SHIPPER, PRODUCED)).isTrue();
        assertThat(readiness.isReady(StepKind.CONFIRM_BOOKING, PRODUCED)).isTrue();
    }

    @Test
    @DisplayName("経路の確定は旅程が読めるまで待つ")
    void waitsForTheItinerary() {
        responses.put("/api/v1/booking/bookings/BK-1/itinerary", "{\"legs\":[]}");
        assertThat(readiness.isReady(StepKind.ASSIGN_ROUTE, PRODUCED)).isFalse();

        responses.put("/api/v1/booking/bookings/BK-1/itinerary",
                "{\"legs\":[{\"voyageNumber\":\"V001\"}]}");
        assertThat(readiness.isReady(StepKind.ASSIGN_ROUTE, PRODUCED)).isTrue();
    }

    @Test
    @DisplayName("追跡番号は追跡の読み口で確かめる（予約側は発行した時点で埋まる）")
    void waitsForTheTrackingProjection() {
        statuses.put("/api/v1/tracking/trackings/TRK-1", 404);
        assertThat(readiness.isReady(StepKind.ISSUE_TRACKING_NUMBER, PRODUCED)).isFalse();

        statuses.put("/api/v1/tracking/trackings/TRK-1", 200);
        assertThat(readiness.isReady(StepKind.ISSUE_TRACKING_NUMBER, PRODUCED)).isTrue();
    }

    @Test
    @DisplayName("荷役は 3 件そろうまで待つ（最後の 1 件だけ見ると途中の欠けを見逃す）")
    void waitsForEveryHandlingRecord() {
        responses.put("/api/v1/handling/TRK-1/activities",
                "{\"items\":[{\"stepNo\":1},{\"stepNo\":2}]}");
        assertThat(readiness.isReady(StepKind.RECORD_HANDLING, PRODUCED)).isFalse();

        responses.put("/api/v1/handling/TRK-1/activities",
                "{\"items\":[{\"stepNo\":1},{\"stepNo\":2},{\"stepNo\":3}]}");
        assertThat(readiness.isReady(StepKind.RECORD_HANDLING, PRODUCED)).isTrue();
    }

    @Test
    @DisplayName("通関は通関済になるまで待つ（審査中では進まない）")
    void waitsForCustomsCleared() {
        responses.put("/api/v1/handling/customs-declarations/DCL-1",
                "{\"status\":\"PENDING\"}");
        assertThat(readiness.isReady(StepKind.CLEAR_CUSTOMS, PRODUCED)).isFalse();

        responses.put("/api/v1/handling/customs-declarations/DCL-1",
                "{\"status\":\"CLEARED\"}");
        assertThat(readiness.isReady(StepKind.CLEAR_CUSTOMS, PRODUCED)).isTrue();
    }

    @Test
    @DisplayName("引取のあとは請求が算出されるまで待つ")
    void waitsForTheInvoiceToBeCalculated() {
        statuses.put("/api/v1/billing/invoices/by-booking/BK-1", 404);
        assertThat(readiness.isReady(StepKind.CLAIM_CARGO, PRODUCED)).isFalse();

        statuses.put("/api/v1/billing/invoices/by-booking/BK-1", 200);
        assertThat(readiness.isReady(StepKind.CLAIM_CARGO, PRODUCED)).isTrue();
    }

    @Test
    @DisplayName("発行と入金は請求の状態で判別する")
    void readsInvoiceStatus() {
        responses.put("/api/v1/billing/invoices/INV-1", "{\"status\":\"CALCULATED\"}");
        assertThat(readiness.isReady(StepKind.ISSUE_INVOICE, PRODUCED)).isFalse();

        responses.put("/api/v1/billing/invoices/INV-1", "{\"status\":\"INVOICED\"}");
        assertThat(readiness.isReady(StepKind.ISSUE_INVOICE, PRODUCED)).isTrue();
        assertThat(readiness.isReady(StepKind.RECORD_PAYMENT, PRODUCED)).isFalse();

        responses.put("/api/v1/billing/invoices/INV-1", "{\"status\":\"PAID\"}");
        assertThat(readiness.isReady(StepKind.RECORD_PAYMENT, PRODUCED)).isTrue();
    }

    @Test
    @DisplayName("読むだけの工程は待たない（既定を「待つ」にすると上限まで待って失敗する）")
    void doesNotWaitForReadOnlySteps() {
        assertThat(readiness.isReady(StepKind.CALCULATE_INVOICE, PRODUCED)).isTrue();
    }

    /** 例外とキャンセルの工程で使う材料。 */
    private static Map<StepKind, String> recovering() {
        var produced = new java.util.LinkedHashMap<StepKind, String>(PRODUCED);
        produced.put(StepKind.REGISTER_EXCEPTION, "exc-1");
        return produced;
    }

    private void tracking(String body) {
        responses.put("/api/v1/tracking/trackings/TRK-1", body);
    }

    @Test
    @DisplayName("US35 §1: 起票は未解決の件数が増えるまで待つ")
    void waitsUntilTheExceptionIsCounted() {
        // **解決済みしかなければ「まだ」。** 件数の列は単票に出ていないので、
        // 明細から数える（読み口に無い項目で待つと、いつまでも追いつかない）。
        tracking("{\"exceptions\":[{\"exceptionId\":\"old\","
                + "\"responseStatus\":\"RESOLVED\"}]}");
        assertThat(readiness.isReady(StepKind.REGISTER_EXCEPTION, recovering())).isFalse();

        tracking("{\"exceptions\":[{\"exceptionId\":\"exc-1\","
                + "\"responseStatus\":\"OPEN\"}]}");
        assertThat(readiness.isReady(StepKind.REGISTER_EXCEPTION, recovering())).isTrue();
    }

    @Test
    @DisplayName("US35 §2: 対応は、その例外の対応状態が変わるまで待つ")
    void waitsForThatExceptionToBeResponding() {
        // **まだ開いただけなら満たさない。**
        tracking("{\"exceptions\":[{\"exceptionId\":\"exc-1\","
                + "\"responseStatus\":\"OPEN\"}]}");
        assertThat(readiness.isReady(StepKind.RESPOND_TO_EXCEPTION, recovering())).isFalse();

        tracking("{\"exceptions\":[{\"exceptionId\":\"exc-1\","
                + "\"responseStatus\":\"RESPONDING\"}]}");
        assertThat(readiness.isReady(StepKind.RESPOND_TO_EXCEPTION, recovering())).isTrue();
    }

    @Test
    @DisplayName("US35 §2: 解決は未解決が 0 に戻るまで待つ")
    void waitsUntilNoExceptionIsOpen() {
        tracking("{\"exceptions\":[{\"exceptionId\":\"exc-1\","
                + "\"responseStatus\":\"RESPONDING\"}]}");
        assertThat(readiness.isReady(StepKind.RESOLVE_EXCEPTION, recovering())).isFalse();

        tracking("{\"exceptions\":[{\"exceptionId\":\"exc-1\","
                + "\"responseStatus\":\"RESOLVED\"}]}");
        assertThat(readiness.isReady(StepKind.RESOLVE_EXCEPTION, recovering())).isTrue();
    }

    @Test
    @DisplayName("US35 §3: 組み直しは「前と違う旅程になった」ではなく「そうなった」で待つ")
    void waitsUntilTheItineraryMatchesWhatWasAssigned() {
        var produced = recovering();
        produced.put(StepKind.REASSIGN_ROUTE, "V-2>JPTYO-USNYC|");
        responses.put("/api/v1/booking/bookings/BK-1/itinerary",
                "{\"legs\":[{\"voyageNumber\":\"V-1\",\"loadUnLocode\":\"JPTYO\","
                + "\"unloadUnLocode\":\"USNYC\"}]}");

        assertThat(readiness.isReady(StepKind.REASSIGN_ROUTE, produced))
                .as("**旅程が入っているか、では最初から満たされる**（空振り）")
                .isFalse();

        responses.put("/api/v1/booking/bookings/BK-1/itinerary",
                "{\"legs\":[{\"voyageNumber\":\"V-2\",\"loadUnLocode\":\"JPTYO\","
                + "\"unloadUnLocode\":\"USNYC\"}]}");
        assertThat(readiness.isReady(StepKind.REASSIGN_ROUTE, produced)).isTrue();
    }

    @Test
    @DisplayName("US35 §4: 申請は承認待ちになるまで、承認は陸揚げ地が追跡へ届くまで待つ")
    void waitsForTheCancellationChain() {
        // **その予約の履歴で待つ。** 承認待ち一覧（S23）は追跡管理者の作業一覧で、
        // シミュレーション由来を既定で外している（[ADR-0020] 決定 4）——
        // そこで待つと、**自分が出した申請が自分には見えず**必ず時間切れになる
        // （IT17 のクローズで実測）。**除外のかかっていない読み口で判別する。**
        String history = "/api/v1/booking/bookings/BK-1/cancellation";
        responses.put(history, "{\"items\":[]}");
        assertThat(readiness.isReady(StepKind.REQUEST_CANCELLATION, recovering())).isFalse();
        responses.put(history,
                "{\"items\":[{\"requestId\":\"r-1\",\"decision\":\"APPROVED\"}]}");
        assertThat(readiness.isReady(StepKind.REQUEST_CANCELLATION, recovering()))
                .as("決着済みの申請では満たさない（待っているのは承認待ちである）")
                .isFalse();
        responses.put(history,
                "{\"items\":[{\"requestId\":\"r-1\",\"decision\":null}]}");
        assertThat(readiness.isReady(StepKind.REQUEST_CANCELLATION, recovering())).isTrue();

        tracking("{}");
        assertThat(readiness.isReady(StepKind.APPROVE_CANCELLATION, recovering()))
                .as("**承認は追跡へ運ばれてから**（予約側だけ見ると連鎖を確かめない）")
                .isFalse();
        tracking("{\"cancellationDischargeUnLocode\":\"SGSIN\"}");
        assertThat(readiness.isReady(StepKind.APPROVE_CANCELLATION, recovering())).isTrue();
    }

    @Test
    @DisplayName("US35 §4: 荷降しは追跡が閉じるまで待つ（承認だけでは閉じない）")
    void waitsUntilTheTrackingIsClosed() {
        tracking("{\"closed\":false}");
        assertThat(readiness.isReady(StepKind.DISCHARGE_CANCELLED, recovering()))
                .as("**承認だけでは追跡は閉じない**（ADR-0018）。貨物はまだ船の上にある")
                .isFalse();

        tracking("{\"closed\":true}");
        assertThat(readiness.isReady(StepKind.DISCHARGE_CANCELLED, recovering())).isTrue();
    }

    @Test
    @DisplayName("US33 §6: 追跡番号の発行は、荷役の写しが追いつくまで待つ")
    void waitsForTheHandlingSnapshotToo() {
        // **次の工程が読む場所で待つ。** 追跡だけ見て進むと、荷役の記録が
        // 「貨物が見つかりません」で止まる（実測）——同じイベントから別の BC が
        // 投影するので、追いつく時刻が違う。
        statuses.put("/api/v1/handling/cargos/TRK-1", 404);
        assertThat(readiness.isReady(StepKind.ISSUE_TRACKING_NUMBER, PRODUCED)).isFalse();

        statuses.remove("/api/v1/handling/cargos/TRK-1");
        assertThat(readiness.isReady(StepKind.ISSUE_TRACKING_NUMBER, PRODUCED)).isTrue();
    }
}
