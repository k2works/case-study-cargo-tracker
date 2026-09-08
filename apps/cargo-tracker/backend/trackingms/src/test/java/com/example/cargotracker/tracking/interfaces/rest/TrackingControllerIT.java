package com.example.cargotracker.tracking.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import static org.awaitility.Awaitility.await;

import com.example.cargotracker.shared.contract.command.InitializeTrackingCommand;
import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import com.example.cargotracker.tracking.infrastructure.projection.TrackingProjection;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.client.RestClient;

/**
 * 追跡一覧・詳細と手動更新の HTTP（S40・S41 / US17・US18）。
 *
 * <p><b>荷主 ID はヘッダから読む。</b> Gateway が JWT から取り出して伝える。
 * クライアントの指定を信じると、他社の追跡まで見えてしまう。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TrackingControllerIT extends AbstractAxonIntegrationTest {

    static class JsonMap extends LinkedHashMap<String, Object> {
        private static final long serialVersionUID = 1L;
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TrackingProjection projection;

    /** 業務タイムゾーンの時計。**検査も実装と同じ時計で「いま」を決める**。 */
    @Autowired
    private java.time.Clock clock;

    @Autowired
    private org.axonframework.messaging.commandhandling.gateway.CommandGateway commands;

    private final RestClient rest = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    private String given(String shipperId) {
        return given(shipperId, Instant.parse("2026-09-08T01:00:00Z"));
    }

    /** 状態が最後に変わった時刻を指定して作る（時間窓の検査に要る）。 */
    private String given(String shipperId, Instant lastChangedAt) {
        String trackingNumber = "TRK-T" + System.nanoTime() % 1000000000L;
        projection.on(new TrackingInitializedEvent(trackingNumber, "b-" + System.nanoTime(),
                shipperId, "JPTYO", "USNYC", "GENERAL",
                List.of(new TrackingInitializedEvent.Leg("V-MOL-001", "JPTYO", "USNYC",
                        Instant.parse("2026-09-10T09:00:00Z"),
                        Instant.parse("2026-09-24T18:00:00Z"))),
                lastChangedAt));
        return trackingNumber;
    }

    /**
     * 追跡を<b>コマンドから</b>作る。
     *
     * <p>状態を動かす検査では投影に行を書くだけでは足りない。集約が無ければ
     * 「始まっていません」で断られる——<b>読み口が緑でも、動かす経路は通らない</b>。</p>
     */
    private String givenAggregate(String shipperId) {
        String trackingNumber = "TRK-A" + System.nanoTime() % 1000000000L;
        commands.sendAndWait(new InitializeTrackingCommand(trackingNumber,
                "b-" + System.nanoTime(), shipperId, "JPTYO", "USNYC", "GENERAL",
                List.of(new InitializeTrackingCommand.LegDto("V-MOL-001", "JPTYO", "USNYC",
                        Instant.parse("2026-09-10T09:00:00Z"),
                        Instant.parse("2026-09-24T18:00:00Z"))),
                Instant.parse("2026-09-08T01:00:00Z")), String.class);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(get("/api/v1/tracking/trackings/" + trackingNumber, null)
                        .getStatusCode()).isEqualTo(HttpStatus.OK));
        return trackingNumber;
    }

    private ResponseEntity<JsonMap> get(String path, String shipperId) {
        return get(path, shipperId, shipperId == null ? "ROLE_TRACKER" : "ROLE_SHIPPER");
    }

    private ResponseEntity<JsonMap> get(String path, String shipperId, String roles) {
        var request = rest.get().uri("http://localhost:" + port + path);
        if (shipperId != null) {
            request = request.header("X-Auth-Shipper-Id", shipperId);
        }
        if (roles != null) {
            request = request.header("X-Auth-Roles", roles);
        }
        return request.retrieve().toEntity(JsonMap.class);
    }

    @Test
    @DisplayName("US17 §1: 追跡管理者は追跡詳細を読める（次に動かせる先も来る）")
    void servesDetailToTrackers() {
        String trackingNumber = given("SHP-000001");

        var response = get("/api/v1/tracking/trackings/" + trackingNumber, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("statusLabel", "未受領");
        // **画面が遷移表を持たない。** 持つと、集約が断る先を画面が出してしまう。
        // **手で選べる先だけが来る。** 誤配は荷役が、例外発生は例外の起票が決める。
        assertThat(response.getBody().get("nextStatuses"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(String.class))
                .containsExactly("RECEIVED");
    }

    @Test
    @DisplayName("US18: 荷主には自社のぶんだけ（他社のものは見つからない）")
    void hidesOtherShippersTrackings() {
        String mine = given("SHP-000001");
        String other = given("SHP-000999");

        assertThat(get("/api/v1/tracking/trackings/" + mine, "SHP-000001").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        // **権限が無いことを伝えない。** 伝えると、その番号が実在すると分かる。
        assertThat(get("/api/v1/tracking/trackings/" + other, "SHP-000001").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("US18: 一覧も荷主で絞る")
    void filtersTheListByShipper() {
        String mine = given("SHP-000002");
        String other = given("SHP-000998");

        var response = get("/api/v1/tracking/trackings?limit=200", "SHP-000002");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody()
                .get("items");
        assertThat(items).extracting(item -> item.get("trackingNumber"))
                .contains(mine)
                .doesNotContain(other);
    }

    @Test
    @DisplayName("US17 §2: 状態を手で更新でき、履歴と現在値に載る")
    void updatesStatusManually() {
        String trackingNumber = givenAggregate("SHP-000001");

        var updated = rest.post()
                .uri("http://localhost:" + port + "/api/v1/tracking/trackings/"
                        + trackingNumber + "/status")
                .header("X-Auth-Username", "tracker01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("newStatus", "RECEIVED", "location", "JPTYO"))
                .retrieve().toBodilessEntity();

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // 投影は非同期に追いつく。
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(get("/api/v1/tracking/trackings/" + trackingNumber, null).getBody())
                        .containsEntry("statusLabel", "受領済"));
        var detail = get("/api/v1/tracking/trackings/" + trackingNumber, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history = (List<Map<String, Object>>) detail.getBody()
                .get("history");
        assertThat(history).hasSize(1);
        // **更新者はヘッダから取る。** 本文に載せると、他人の名前で記録できる。
        assertThat(history.get(0)).containsEntry("recordedBy", "tracker01");
        assertThat(history.get(0)).containsEntry("eventType", "MANUAL");
    }

    @Test
    @DisplayName("正典が許さない遷移は断る（500 にしない）")
    void rejectsForbiddenTransitions() {
        String trackingNumber = givenAggregate("SHP-000001");

        var response = rest.post()
                .uri("http://localhost:" + port + "/api/v1/tracking/trackings/"
                        + trackingNumber + "/status")
                .header("X-Auth-Username", "tracker01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("newStatus", "DELIVERED"))
                .retrieve().toEntity(JsonMap.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("message").toString())
                .as("断った理由が読めないと、追跡管理者は次に何をすればよいか分からない")
                .contains("未受領")
                .contains("引取済");
    }

    @Test
    @DisplayName("知らない状態の名前は 422（壊れたのではなく入力の誤り）")
    void rejectsUnknownStatusNames() {
        String trackingNumber = givenAggregate("SHP-000001");

        var response = rest.post()
                .uri("http://localhost:" + port + "/api/v1/tracking/trackings/"
                        + trackingNumber + "/status")
                .header("X-Auth-Username", "tracker01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("newStatus", "FLYING"))
                .retrieve().toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.valueOf(422));
    }

    @Test
    @DisplayName("US17: 一覧は到着予定が近い順に出て、切れていれば全件数が分かる")
    void ordersByEstimatedArrivalAndReportsTotal() {
        given("SHP-000003");

        var response = get("/api/v1/tracking/trackings?limit=200", "SHP-000003");

        // **上限で切れていることを黙らない。** 出ていない貨物は誰も追わない。
        assertThat(response.getBody().get("total")).isNotNull();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody()
                .get("items");
        assertThat(items).isNotEmpty();
    }

    @Test
    @DisplayName("場所を空で更新しても、それまでの現在地が消えない")
    void keepsTheLastKnownLocation() {
        String trackingNumber = givenAggregate("SHP-000001");

        rest.post().uri("http://localhost:" + port + "/api/v1/tracking/trackings/"
                        + trackingNumber + "/status")
                .header("X-Auth-Username", "tracker01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("newStatus", "RECEIVED", "location", "JPTYO"))
                .retrieve().toBodilessEntity();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(get("/api/v1/tracking/trackings/" + trackingNumber, null).getBody())
                        .containsEntry("currentUnLocode", "JPTYO"));

        // 場所を入れずに次の状態へ。**荷受人にとって現在地は照会の主目的**なので、
        // 分かっていた場所が消えると業務が止まる。
        rest.post().uri("http://localhost:" + port + "/api/v1/tracking/trackings/"
                        + trackingNumber + "/status")
                .header("X-Auth-Username", "tracker01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("newStatus", "LOADED"))
                .retrieve().toBodilessEntity();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(get("/api/v1/tracking/trackings/" + trackingNumber, null).getBody())
                        .containsEntry("statusLabel", "積込済")
                        .containsEntry("currentUnLocode", "JPTYO"));
    }

    @Test
    @DisplayName("荷主 ID の紐付いていない荷主には見せない（フェイルオープンにしない）")
    void refusesShippersWithoutAShipperId() {
        // **「荷主 ID が無ければ全件」にしない。** 紐付けの無い ROLE_SHIPPER が
        // 1 人でも作られると、その人に全社の追跡が見える（user_shipper_link は
        // NULL を許し、JWT も claim ごと落とす）。
        given("SHP-000004");

        assertThat(get("/api/v1/tracking/trackings", null, "ROLE_SHIPPER").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/tracking/trackings/TRK-ANY0000001", null, "ROLE_SHIPPER")
                .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ---- US19 例外（IT10 T6・T7） ----

    private ResponseEntity<Void> post(String path, Map<String, Object> body) {
        return rest.post().uri("http://localhost:" + port + path)
                .header("X-Auth-Username", "tracker01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toBodilessEntity();
    }

    @Test
    @DisplayName("US19 §1・§2・§5: 遅延を起票すると例外発生になり、一覧に出る")
    void registersAndListsException() {
        String trackingNumber = givenAggregate("SHP-000001");
        String exceptionId = "ex-" + System.nanoTime();

        var registered = post("/api/v1/tracking/trackings/" + trackingNumber + "/exceptions",
                Map.of("exceptionId", exceptionId, "exceptionType", "DELAY",
                        "unLocode", "SGSIN", "description", "台風で 3 日遅れます"));

        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var detail = get("/api/v1/tracking/trackings/" + trackingNumber, null);
            assertThat(detail.getBody()).containsEntry("statusLabel", "例外発生");

            var list = get("/api/v1/tracking/trackings/exceptions", null);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items =
                    (List<Map<String, Object>>) list.getBody().get("items");
            assertThat(items).anySatisfy(item -> {
                assertThat(item).containsEntry("exceptionId", exceptionId);
                // **利用者に列挙名を見せない。**
                assertThat(item).containsEntry("exceptionTypeLabel", "遅延");
                assertThat(item).containsEntry("responseStatusLabel", "起票");
                assertThat(item).containsEntry("urgent", false);
            });
        });
    }

    @Test
    @DisplayName("US19 §3・§4: 通知の記録・対応開始・解決を通すと例外前の状態へ戻る")
    void respondsAndResolves() {
        String trackingNumber = givenAggregate("SHP-000001");
        // 受領まで進めてから起票する（戻る先が未受領では区別が付かない）。
        post("/api/v1/tracking/trackings/" + trackingNumber + "/status",
                Map.of("newStatus", "RECEIVED", "location", "JPTYO"));
        String exceptionId = "ex-" + System.nanoTime();
        post("/api/v1/tracking/trackings/" + trackingNumber + "/exceptions",
                Map.of("exceptionId", exceptionId, "exceptionType", "DELAY",
                        "unLocode", "SGSIN", "description", "台風で 3 日遅れます"));

        String base = "/api/v1/tracking/trackings/" + trackingNumber + "/exceptions/"
                + exceptionId;
        assertThat(post(base + "/notifications",
                Map.of("means", "電話", "summary", "3 日遅れる見込みと伝えました"))
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(post(base + "/response",
                Map.of("newEstimatedArrival", "2026-09-27", "plan", "代替便を手配中"))
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(post(base + "/resolution",
                Map.of("resolution", "代替便に振り替えました"))
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            // **例外前の状態へ戻る**（不変条件 5）。
            assertThat(get("/api/v1/tracking/trackings/" + trackingNumber, null).getBody())
                    .containsEntry("statusLabel", "受領済");

            var list = get("/api/v1/tracking/trackings/exceptions", null);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items =
                    (List<Map<String, Object>>) list.getBody().get("items");
            assertThat(items)
                    .as("解決済は既定で外す（決着したものが混ざると一覧が信用されない）")
                    .noneSatisfy(item ->
                            assertThat(item).containsEntry("exceptionId", exceptionId));
        });
    }

    @Test
    @DisplayName("知らない例外種別は 422（壊れたのではなく入力の誤り）")
    void rejectsUnknownExceptionType() {
        String trackingNumber = givenAggregate("SHP-000001");

        var response = rest.post()
                .uri("http://localhost:" + port + "/api/v1/tracking/trackings/"
                        + trackingNumber + "/exceptions")
                .header("X-Auth-Username", "tracker01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("exceptionId", "ex-x", "exceptionType", "TYPHOON",
                        "description", "台風"))
                .exchange((req, res) -> res.getStatusCode());

        assertThat(response).isEqualTo(HttpStatus.valueOf(422));
    }

    @Test
    @DisplayName("負の件数でも壊れない（500 にしない）")
    void clampsTheLimit() {
        assertThat(get("/api/v1/tracking/trackings?limit=-1", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("S02 荷主: 直近で状態が変わった件数を自社分だけ数える")
    void countsRecentlyChangedForTheShipperOnly() {
        // **荷主には通知が届かない**（送信基盤はスコープ外）。件数で気づかせて一覧へ繋ぐ。
        String mine = "SHP-RC0001";
        given(mine);
        given("SHP-RC0002");

        var response = get("/api/v1/tracking/trackings/recently-changed?withinHours=100000", mine);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Integer) response.getBody().get("count")).isEqualTo(1);
    }

    @Test
    @DisplayName("S02 荷主: 時間窓の外で状態が変わったものは数えない（既定の 24 時間）")
    void doesNotCountChangesOutsideTheWindow() {
        // **IT9 の検査は withinHours=100000 で窓を無効化していた。** それでは
        // since を業務タイムゾーンの時計から出す実装を潰しても緑になる。
        // **実装と同じ時計で「いま」を決める**（業務タイムゾーン）。JVM 既定の
        // now() を使うと、時差の分だけ境界がずれて CI だけ落ちる。
        Instant now = clock.instant();
        String mine = "SHP-RC0004";
        given(mine, now.minus(Duration.ofHours(1)));
        given(mine, now.minus(Duration.ofDays(30)));

        var response = get("/api/v1/tracking/trackings/recently-changed", mine);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Integer) response.getBody().get("count"))
                .as("30 日前の変更は「直近 24 時間」ではない")
                .isEqualTo(1);
        assertThat((Integer) response.getBody().get("withinHours")).isEqualTo(24);
    }

    @Test
    @DisplayName("S02 追跡管理者にはこの受け皿を出さない（自分の仕事ではない）")
    void doesNotCountForTrackers() {
        given("SHP-RC0003");

        var response = get("/api/v1/tracking/trackings/recently-changed?withinHours=100000", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Integer) response.getBody().get("count")).isZero();
    }
}
