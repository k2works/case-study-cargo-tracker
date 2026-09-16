package com.example.cargotracker.routing.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.routing.domain.model.events.VoyageRegisteredEvent;
import com.example.cargotracker.routing.infrastructure.projection.VoyageProjection;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
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
 * 弾いた航海が<b>経路設計者の要確認一覧に現れる</b>ことを、記録側でなく読み口から見る。
 *
 * <p>投影の単体テスト（{@code VoyageProjectionIT}）は「{@code attention_item} に
 * 行が入ったこと」しか見ない。読み出す経路が無ければ、行は入るのに画面には何も出ない。
 * 記録と読み口は対で確かめる。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AttentionItemControllerIT extends AbstractAxonIntegrationTest {

    private static final Instant DEPART = Instant.parse("2026-10-10T09:00:00Z");
    private static final Instant ARRIVE = Instant.parse("2026-10-24T18:00:00Z");

    static class JsonMap extends LinkedHashMap<String, Object> {
        private static final long serialVersionUID = 1L;
    }

    @LocalServerPort
    private int port;

    @Autowired
    private VoyageProjection projection;

    private final RestClient rest = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    private ResponseEntity<JsonMap> listAs(String rolesHeader) {
        var request = rest.get()
                .uri("http://localhost:" + port + "/api/v1/routing/attention-items");
        if (rolesHeader != null) {
            request = request.header("X-Auth-Roles", rolesHeader);
        }
        return request.retrieve().toEntity(JsonMap.class);
    }

    /** 航海番号は 20 文字まで。ナノ秒をそのまま繋ぐと桁あふれする。 */
    private static String uniqueNumber() {
        return "VA-" + Long.toString(System.nanoTime(), 36);
    }

    private void register(String number, String vesselName) {
        projection.on(new VoyageRegisteredEvent(number, "MOL", "商船三井", vesselName,
                List.of(new VoyageRegisteredEvent.Movement("JPTYO", "USNYC", DEPART, ARRIVE)),
                List.of("GENERAL"), "routing01"));
    }

    @Test
    @DisplayName("一意制約で弾いた航海が経路設計者の要確認一覧に出る")
    void rejectedVoyageAppearsForRoutingRole() {
        String number = uniqueNumber();
        register(number, "MOL EXPRESS");
        // 同じ番号で別の船名。投影は弾き、要確認に記録する。
        register(number, "ONE HARMONY");

        String body = String.valueOf(listAs("ROLE_ROUTING").getBody().get("items"));

        assertThat(body)
                .as("記録しても読み口が無ければ、経路設計者は弾かれたことに気づけない")
                .contains(number)
                .contains("航海番号の重複");
    }

    @Test
    @DisplayName("他ロールには経路設計者宛の要確認は出ない")
    void hidesRoutingItemsFromOtherRoles() {
        String number = uniqueNumber();
        register(number, "MOL EXPRESS");
        register(number, "ONE HARMONY");

        String body = String.valueOf(listAs("ROLE_SALES").getBody().get("items"));

        assertThat(body).doesNotContain(number);
    }

    private ResponseEntity<JsonMap> acknowledgeAs(String itemId, String rolesHeader,
            String username) {
        var request = rest.post().uri("http://localhost:" + port
                + "/api/v1/routing/attention-items/" + itemId + "/acknowledge");
        if (rolesHeader != null) {
            request = request.header("X-Auth-Roles", rolesHeader);
        }
        if (username != null) {
            request = request.header("X-Auth-Username", username);
        }
        return request.retrieve().toEntity(JsonMap.class);
    }

    @Test
    @DisplayName("確認済にすると一覧から消え、誰がいつ確認したかが残る（IT14 引き継ぎ A）")
    void acknowledgingRemovesItemFromList() {
        String number = uniqueNumber();
        register(number, "MOL EXPRESS");
        register(number, "ONE HARMONY");
        // 識別子は採番せず事実から導く（共有カーネル）。導出をテスト側に写さない。
        String itemId = com.example.cargotracker.shared.domain.attention.AttentionItemId
                .of("PROJECTION_REJECTED", "VOYAGE", number, "航海番号の重複").value();

        // 確認する前は出ている。**この行が無いと、消えたのか元から無かったのかを
        // 判別できない。**
        assertThat(String.valueOf(listAs("ROLE_ROUTING").getBody().get("items")))
                .contains(number);

        ResponseEntity<JsonMap> response = acknowledgeAs(itemId, "ROLE_ROUTING", "routing01");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("acknowledgedBy")).isEqualTo("routing01");
        assertThat(String.valueOf(listAs("ROLE_ROUTING").getBody().get("items")))
                .as("確認済は担当の一覧から外れる")
                .doesNotContain(number);
    }

    @Test
    @DisplayName("他ロールは確認できない（見えないものを片づけられてはいけない）")
    void cannotAcknowledgeFromAnotherRole() {
        String number = uniqueNumber();
        register(number, "MOL EXPRESS");
        register(number, "ONE HARMONY");
        String itemId = com.example.cargotracker.shared.domain.attention.AttentionItemId
                .of("PROJECTION_REJECTED", "VOYAGE", number, "航海番号の重複").value();

        assertThat(acknowledgeAs(itemId, "ROLE_SALES", "sales01").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(String.valueOf(listAs("ROLE_ROUTING").getBody().get("items")))
                .as("担当の一覧には残ったまま")
                .contains(number);
    }

    @Test
    @DisplayName("ロールが伝わっていなければ何も出さない")
    void showsNothingWithoutRoles() {
        String number = uniqueNumber();
        register(number, "MOL EXPRESS");
        register(number, "ONE HARMONY");

        ResponseEntity<JsonMap> response = listAs(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(String.valueOf(response.getBody().get("items")))
                .as("既定で経路設計者宛を出すと、伝達が壊れていることに気づけない")
                .isEqualTo("[]");
    }
}
