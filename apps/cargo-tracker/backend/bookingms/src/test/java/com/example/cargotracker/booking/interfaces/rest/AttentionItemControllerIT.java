package com.example.cargotracker.booking.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.infrastructure.projection.AttentionItemRecorder;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.time.Instant;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.client.RestClient;

/**
 * 要確認一覧は<b>自分の担当宛だけ</b>を出す（S70）。
 *
 * <p>ロールは Gateway が JWT から取り出して {@code X-Auth-Roles} で伝える。
 * クライアントの指定を信じると、他ロール宛の要確認まで見えてしまう。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AttentionItemControllerIT extends AbstractAxonIntegrationTest {

    /**
     * 要確認が起きた時刻。
     *
     * <p><b>固定した 1 点を使う。</b> 検査したいのは「誰宛に出るか・片づけたら
     * 消えるか」であって、時刻そのものではない。JVM 既定の現在時刻を使うと、
     * 何を検査しているのかが読みにくくなる（CI は UTC で回るので、時刻の絡む
     * 検査を足したときに時差でずれる形も招く）。</p>
     */
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-28T01:00:00Z");

    static class JsonMap extends LinkedHashMap<String, Object> {
        private static final long serialVersionUID = 1L;
    }

    @LocalServerPort
    private int port;

    @Autowired
    private AttentionItemRecorder recorder;

    private final RestClient rest = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    private ResponseEntity<JsonMap> listAs(String rolesHeader) {
        var request = rest.get().uri("http://localhost:" + port + "/api/v1/booking/attention-items");
        if (rolesHeader != null) {
            request = request.header("X-Auth-Roles", rolesHeader);
        }
        return request.retrieve().toEntity(JsonMap.class);
    }

    @Test
    @DisplayName("自分の担当宛だけが出て、他ロール宛は出ない")
    void showsOnlyItemsForCallerRoles() {
        String salesTarget = "sales-" + System.nanoTime();
        String accountantTarget = "acct-" + System.nanoTime();
        recorder.add("PROJECTION_REJECTED", "SHIPPER", salesTarget, "ROLE_SALES",
                "メールアドレスの重複", "{}", OCCURRED_AT);
        recorder.add("PROJECTION_REJECTED", "INVOICE", accountantTarget, "ROLE_ACCOUNTANT",
                "荷主が見つからない", "{}", OCCURRED_AT);

        String forSales = String.valueOf(listAs("ROLE_SALES").getBody().get("items"));

        assertThat(forSales).contains(salesTarget);
        assertThat(forSales)
                .as("他ロール宛が見えるのは情報の見せすぎ")
                .doesNotContain(accountantTarget);
    }

    @Test
    @DisplayName("複数のロールを持つ利用者には両方の担当分が出る")
    void mergesItemsForMultipleRoles() {
        String salesTarget = "sales-" + System.nanoTime();
        String trackerTarget = "trk-" + System.nanoTime();
        recorder.add("PROJECTION_REJECTED", "SHIPPER", salesTarget, "ROLE_SALES",
                "メールアドレスの重複", "{}", OCCURRED_AT);
        recorder.add("REACTION_FAILED", "CARGO", trackerTarget, "ROLE_TRACKER",
                "追跡の初期化が届かない", "{}", OCCURRED_AT);

        String body = String.valueOf(listAs("ROLE_SALES,ROLE_TRACKER").getBody().get("items"));

        assertThat(body).contains(salesTarget).contains(trackerTarget);
    }

    private ResponseEntity<JsonMap> acknowledgeAs(String itemId, String rolesHeader,
            String username) {
        var request = rest.post().uri("http://localhost:" + port
                + "/api/v1/booking/attention-items/" + itemId + "/acknowledge");
        if (rolesHeader != null) {
            request = request.header("X-Auth-Roles", rolesHeader);
        }
        if (username != null) {
            request = request.header("X-Auth-Username", username);
        }
        return request.retrieve().toEntity(JsonMap.class);
    }

    @Test
    @DisplayName("確認済にすると一覧から消え、誰がいつ確認したかが残る")
    void acknowledgingRemovesItemFromListAndKeepsTheTrail() {
        String target = "ack-" + System.nanoTime();
        recorder.add("PROJECTION_REJECTED", "SHIPPER", target, "ROLE_SALES",
                "メールアドレスの重複", "{}", OCCURRED_AT);
        String itemId = itemIdOf("ROLE_SALES", target);

        // 確認する前は出ている。**この行が無いと、消えたのか元から無かったのか
        // 判別できない**（条件を外しても緑になる検査を書かない）。
        assertThat(String.valueOf(listAs("ROLE_SALES").getBody().get("items")))
                .contains(target);

        ResponseEntity<JsonMap> response = acknowledgeAs(itemId, "ROLE_SALES", "sales01");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("acknowledgedBy")).isEqualTo("sales01");
        assertThat(response.getBody().get("acknowledgedAt")).isNotNull();
        assertThat(String.valueOf(listAs("ROLE_SALES").getBody().get("items")))
                .as("確認済は担当の一覧から外れる（残ると、確認した意味が無い）")
                .doesNotContain(target);
    }

    @Test
    @DisplayName("他ロール宛は確認できない（見えないものを片づけられてはいけない）")
    void cannotAcknowledgeAnotherRolesItem() {
        String target = "foreign-" + System.nanoTime();
        recorder.add("PROJECTION_REJECTED", "INVOICE", target, "ROLE_ACCOUNTANT",
                "荷主が見つからない", "{}", OCCURRED_AT);
        String itemId = itemIdOf("ROLE_ACCOUNTANT", target);

        assertThat(acknowledgeAs(itemId, "ROLE_SALES", "sales01").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(String.valueOf(listAs("ROLE_ACCOUNTANT").getBody().get("items")))
                .as("担当の一覧には残ったまま")
                .contains(target);
    }

    @Test
    @DisplayName("確認済をもう一度確認しようとしても跡を上書きしない")
    void doesNotOverwriteAnExistingAcknowledgement() {
        String target = "twice-" + System.nanoTime();
        recorder.add("PROJECTION_REJECTED", "SHIPPER", target, "ROLE_SALES",
                "メールアドレスの重複", "{}", OCCURRED_AT);
        String itemId = itemIdOf("ROLE_SALES", target);

        acknowledgeAs(itemId, "ROLE_SALES", "sales01");

        assertThat(acknowledgeAs(itemId, "ROLE_SALES", "sales02").getStatusCode())
                .as("2 度目は対象が無い。最初に確認した人の跡が正")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("ロールが伝わっていなければ確認もできない")
    void cannotAcknowledgeWithoutRoles() {
        String target = "noroles-" + System.nanoTime();
        recorder.add("PROJECTION_REJECTED", "SHIPPER", target, "ROLE_SALES",
                "メールアドレスの重複", "{}", OCCURRED_AT);

        assertThat(acknowledgeAs(itemIdOf("ROLE_SALES", target), null, "sales01").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    /** 識別子は採番せず事実から導く（共有カーネル）。テスト側で導出を写さない。 */
    private static String itemIdOf(String role, String target) {
        String targetType = "ROLE_ACCOUNTANT".equals(role) ? "INVOICE" : "SHIPPER";
        String reason = "ROLE_ACCOUNTANT".equals(role) ? "荷主が見つからない" : "メールアドレスの重複";
        return com.example.cargotracker.shared.domain.attention.AttentionItemId
                .of("PROJECTION_REJECTED", targetType, target, reason).value();
    }

    @Test
    @DisplayName("ロールが伝わっていなければ何も出さない")
    void showsNothingWithoutRoles() {
        recorder.add("PROJECTION_REJECTED", "SHIPPER", "orphan-" + System.nanoTime(),
                "ROLE_SALES", "メールアドレスの重複", "{}", OCCURRED_AT);

        ResponseEntity<JsonMap> response = listAs(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(String.valueOf(response.getBody().get("items")))
                .as("既定で営業宛を出すと、伝達が壊れていることに気づけない")
                .isEqualTo("[]");
    }
}
