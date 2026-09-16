package com.example.cargotracker.billing.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.billing.infrastructure.persistence.AttentionItemMapper;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
 * 経理宛の要確認の API（S70 が読む・IT14 引き継ぎ A）。
 *
 * <p><b>記録するだけ・読めるだけでは仕事が終わらない。</b> 片づけた印が無いと、
 * 同じ行を毎朝読み直すことになり、件数もいつまでも減りません。</p>
 *
 * <p><b>見えない行を片づけられてはいけない。</b> 一覧に出す条件と同じ条件を更新にも
 * 置く——判定をもう 1 か所に書き直すと、片方だけが緩くなる。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AttentionItemControllerIT extends AbstractAxonIntegrationTest {

    private static final Instant AT = Instant.parse("2026-09-28T01:00:00Z");
    private static final String ACCOUNTANT = "ROLE_ACCOUNTANT";

    @LocalServerPort
    private int port;

    @Autowired
    private AttentionItemMapper attentionItems;

    private final RestClient rest = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    private static final org.springframework.core.ParameterizedTypeReference<Map<String, Object>>
            JSON = new org.springframework.core.ParameterizedTypeReference<>() { };

    private String url(String path) {
        return "http://localhost:" + port + "/api/v1/billing/attention-items" + path;
    }

    /** 要確認を 1 件置く。**宛先のロールを変えられる**（他ロール宛を試すため）。 */
    private String open(String assignedRole) {
        String itemId = String.valueOf(System.nanoTime());
        attentionItems.insert(new AttentionItemMapper.AttentionItemRow(itemId,
                "PROJECTION_REJECTED", "BOOKING", "B-AT-" + itemId, assignedRole,
                "重量が分からないので請求書を作れません", "{}", AT, null, null));
        return itemId;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listAs(String roles) {
        var request = rest.get().uri(url(""));
        if (roles != null) {
            request = request.header("X-Auth-Roles", roles);
        }
        return (List<Map<String, Object>>) request.retrieve().toEntity(JSON)
                .getBody().get("items");
    }

    private ResponseEntity<Map<String, Object>> acknowledge(String itemId, String roles,
            String username) {
        var request = rest.post().uri(url("/" + itemId + "/acknowledge"));
        if (roles != null) {
            request = request.header("X-Auth-Roles", roles);
        }
        if (username != null) {
            request = request.header("X-Auth-Username", username);
        }
        return request.retrieve().toEntity(JSON);
    }

    @Test
    @DisplayName("自ロール宛だけが出る（他ロール宛は出ない）")
    void listsOnlyTheOwnRole() {
        String mine = open(ACCOUNTANT);
        String others = open("ROLE_ROUTING");

        assertThat(listAs(ACCOUNTANT)).extracting(item -> item.get("itemId"))
                .contains(mine).doesNotContain(others);
    }

    @Test
    @DisplayName("ロールが伝わっていなければ何も出さない（既定を置かない）")
    void listsNothingWithoutRoles() {
        open(ACCOUNTANT);

        assertThat(listAs(null))
                .as("既定を置くと、伝達が壊れていても気づかないまま他ロール宛が見える")
                .isEmpty();
        assertThat(listAs("  ")).isEmpty();
    }

    @Test
    @DisplayName("確認済にすると一覧から消え、誰がいつ確認したかが返る")
    void acknowledgesAndLeavesATrace() {
        String itemId = open(ACCOUNTANT);

        var response = acknowledge(itemId, ACCOUNTANT, "accountant01");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("acknowledgedBy")).isEqualTo("accountant01");
        assertThat(response.getBody().get("acknowledgedAt"))
                .as("「消えた」だけでは、押したことが残らない")
                .isNotNull();
        assertThat(listAs(ACCOUNTANT)).extracting(item -> item.get("itemId"))
                .doesNotContain(itemId);
    }

    @Test
    @DisplayName("二度目は 404（最初に確認した人の跡を上書きしない）")
    void doesNotOverwriteAnExistingTrace() {
        String itemId = open(ACCOUNTANT);
        acknowledge(itemId, ACCOUNTANT, "accountant01");

        assertThat(acknowledge(itemId, ACCOUNTANT, "accountant02").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("他ロール宛は確認できない（見えないものを片づけられてはいけない）")
    void refusesToAcknowledgeAnotherRolesItem() {
        String others = open("ROLE_ROUTING");

        assertThat(acknowledge(others, ACCOUNTANT, "accountant01").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("ロールが伝わっていなければ確認もできない")
    void refusesToAcknowledgeWithoutRoles() {
        String itemId = open(ACCOUNTANT);

        assertThat(acknowledge(itemId, null, "accountant01").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(acknowledge(itemId, " , ", "accountant01").getStatusCode())
                .as("区切りだけの値も「伝わっていない」と同じに扱う")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("誰が確認したか分からない跡は残さない")
    void refusesToAcknowledgeWithoutAUsername() {
        String itemId = open(ACCOUNTANT);

        assertThat(acknowledge(itemId, ACCOUNTANT, null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(acknowledge(itemId, ACCOUNTANT, "  ").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
