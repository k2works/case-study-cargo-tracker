package com.example.cargotracker.acceptance.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.shared.contract.command.InitializeTrackingCommand;
import io.cucumber.java.ja.かつ;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.前提;
import io.cucumber.java.ja.もし;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * 追跡の照会（US18）と状態の手動更新（US17）のステップ。
 *
 * <p>trackingms を実際に起動し、API を叩いて確かめる。集約や投影を直接呼ぶと
 * 「荷受人・追跡管理者から見てどうなるか」を判別できない。</p>
 */
public class TrackingSteps {

    static class JsonMap extends LinkedHashMap<String, Object> {
        private static final long serialVersionUID = 1L;
    }

    private static final String SHIPPER = "SHP-000001";

    @LocalServerPort
    private int trackingPort;

    @Autowired
    private CommandGateway commands;

    private final RestClient rest = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    private String trackingNumber;
    private ResponseEntity<JsonMap> lastResponse;

    private String url(String path) {
        return "http://localhost:" + trackingPort + path;
    }

    @前提("予約 {string} の追跡番号が発行され、追跡が始まっている")
    public void 追跡が始まっている(String bookingId) {
        trackingNumber = "TRK-B" + System.nanoTime() % 1000000000L;
        commands.sendAndWait(new InitializeTrackingCommand(trackingNumber,
                bookingId + "-" + System.nanoTime(), SHIPPER, "JPTYO", "USNYC", "GENERAL",
                List.of(new InitializeTrackingCommand.LegDto("V-MOL-001", "JPTYO", "USNYC",
                        Instant.parse("2026-09-10T09:00:00Z"),
                        Instant.parse("2026-09-24T18:00:00Z"))),
                Instant.parse("2026-09-08T01:00:00Z")), String.class);

        // 投影は非同期に追いつく。
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(publicLookup(trackingNumber).getStatusCode()).isEqualTo(HttpStatus.OK));
    }

    private ResponseEntity<JsonMap> publicLookup(String number) {
        return rest.get().uri(url("/api/v1/tracking/public/" + number))
                .retrieve().toEntity(JsonMap.class);
    }

    @もし("追跡番号で照会する")
    public void 追跡番号で照会する() {
        lastResponse = publicLookup(trackingNumber);
    }

    @もし("存在しない追跡番号 {string} で照会する")
    public void 存在しない追跡番号で照会する(String number) {
        lastResponse = publicLookup(number);
    }

    @ならば("状態と区間と到着予定が読める")
    public void 状態と区間と到着予定が読める() {
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(lastResponse.getBody())
                .containsEntry("statusLabel", "未受領")
                .containsEntry("originUnLocode", "JPTYO")
                .containsEntry("destinationUnLocode", "USNYC");
        assertThat(lastResponse.getBody().get("estimatedArrival"))
                .as("到着予定は予定の旅程の最終区間の荷降しから決まる")
                .isNotNull();
    }

    @かつ("認証は求められない")
    public void 認証は求められない() {
        // 上の照会は Authorization も X-Auth-* も送っていない。**荷受人はロールを
        // 持たない**ので、ここが 401 になると社外からは使えない。
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @ならば("見つからないとだけ返る")
    public void 見つからないとだけ返る() {
        // 存在しない番号と権限の無い番号を区別しない。区別すると、総当たりで
        // 「実在するが自分のものではない番号」を選り分けられる。
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @ならば("応答に荷主 ID と予約 ID が入っていない")
    public void 応答に社内の情報が入っていない() {
        assertThat(lastResponse.getBody())
                .doesNotContainKey("shipperId")
                .doesNotContainKey("bookingId");
        assertThat(lastResponse.getBody().toString()).doesNotContain(SHIPPER);
    }

    @もし("追跡管理者が状態を {string} に更新する")
    public void 追跡管理者が状態を更新する(String newStatus) {
        lastResponse = rest.post()
                .uri(url("/api/v1/tracking/trackings/" + trackingNumber + "/status"))
                .header("X-Auth-Username", "tracker01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("newStatus", newStatus, "location", "JPTYO"))
                .retrieve().toEntity(JsonMap.class);
    }

    @ならば("現在の状態が {string} になる")
    public void 現在の状態になる(String statusLabel) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(detail(null).getBody()).containsEntry("statusLabel", statusLabel));
    }

    @かつ("現在の状態が {string} のままである")
    public void 現在の状態のままである(String statusLabel) {
        assertThat(detail(null).getBody()).containsEntry("statusLabel", statusLabel);
    }

    @かつ("履歴に手動更新として 1 件残る")
    public void 履歴に手動更新として残る() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history =
                (List<Map<String, Object>>) detail(null).getBody().get("history");
        assertThat(history).hasSize(1);
        assertThat(history.get(0)).containsEntry("eventType", "MANUAL");
        assertThat(history.get(0)).containsEntry("recordedBy", "tracker01");
    }

    @ならば("断られる")
    public void 断られる() {
        // **状態コードと理由まで見る。** 2xx でないことだけを見ると、404 でも
        // 500 でも緑になり「遷移表が許さないから断った」ことを判別しない。
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(lastResponse.getBody().get("message").toString())
                .contains("未受領")
                .contains("引取済");
    }

    @もし("別の荷主として追跡を開く")
    public void 別の荷主として追跡を開く() {
        lastResponse = detail("SHP-000999");
    }

    /**
     * 追跡詳細を開く。<b>Gateway が実際に送る形</b>で送る。
     *
     * <p>ロールと荷主 ID は Gateway が JWT から取り出して付ける。片方だけを
     * 送る検査にすると、本番では起きない組合せで緑になる。</p>
     */
    private ResponseEntity<JsonMap> detail(String shipperId) {
        var request = rest.get().uri(url("/api/v1/tracking/trackings/" + trackingNumber))
                .header("X-Auth-Roles", shipperId == null ? "ROLE_TRACKER" : "ROLE_SHIPPER");
        if (shipperId != null) {
            request = request.header("X-Auth-Shipper-Id", shipperId);
        }
        return request.retrieve().toEntity(JsonMap.class);
    }
}
