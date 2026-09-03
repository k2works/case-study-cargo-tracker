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
        recorder.record("PROJECTION_REJECTED", "SHIPPER", salesTarget, "ROLE_SALES",
                "メールアドレスの重複", "{}", Instant.now());
        recorder.record("PROJECTION_REJECTED", "INVOICE", accountantTarget, "ROLE_ACCOUNTANT",
                "荷主が見つからない", "{}", Instant.now());

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
        recorder.record("PROJECTION_REJECTED", "SHIPPER", salesTarget, "ROLE_SALES",
                "メールアドレスの重複", "{}", Instant.now());
        recorder.record("REACTION_FAILED", "CARGO", trackerTarget, "ROLE_TRACKER",
                "追跡の初期化が届かない", "{}", Instant.now());

        String body = String.valueOf(listAs("ROLE_SALES,ROLE_TRACKER").getBody().get("items"));

        assertThat(body).contains(salesTarget).contains(trackerTarget);
    }

    @Test
    @DisplayName("ロールが伝わっていなければ何も出さない")
    void showsNothingWithoutRoles() {
        recorder.record("PROJECTION_REJECTED", "SHIPPER", "orphan-" + System.nanoTime(),
                "ROLE_SALES", "メールアドレスの重複", "{}", Instant.now());

        ResponseEntity<JsonMap> response = listAs(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(String.valueOf(response.getBody().get("items")))
                .as("既定で営業宛を出すと、伝達が壊れていることに気づけない")
                .isEqualTo("[]");
    }
}
