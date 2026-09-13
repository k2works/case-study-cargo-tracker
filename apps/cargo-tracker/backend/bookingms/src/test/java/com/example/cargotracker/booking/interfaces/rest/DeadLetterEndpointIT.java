package com.example.cargotracker.booking.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.client.RestClient;

/**
 * 退避したイベントを画面から読める（S91 / ADR-0014・IT15 引き継ぎ 1）。
 *
 * <p><b>運用の道具だけでは足りなかった。</b> gulp タスクと Actuator はどちらも
 * 端末からしか触れず、投影が止まっていることに気づけるのは端末を持つ人だけ
 * だった。</p>
 *
 * <p><b>中身（payload）を返さないことを、ここで固定する。</b> 退避には個人情報が
 * 載りうる（ADR-0003）。「返していないつもり」は、列を足したときに黙って破れる。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DeadLetterEndpointIT extends AbstractAxonIntegrationTest {

    static class JsonMap extends LinkedHashMap<String, Object> {
        private static final long serialVersionUID = 1L;
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    /** Axon が書く形の 1 行を直接入れる（毒を作らずに読み口だけを見る）。 */
    private String park(String causeMessage) {
        String id = "dl-" + System.nanoTime();
        // 列は検査ごとに分ける。**同じ列に 2 度入れると UNIQUE で落ちる**
        // （退避は「同じ列の後続」も順に並べるので、索引が一意）。
        String sequence = "B-2026-0902-" + System.nanoTime() % 100000;
        jdbc.update("""
                INSERT INTO dead_letter_entry (
                    dead_letter_id, processing_group, sequence_identifier, sequence_index,
                    event_type, event_identifier, type, event_timestamp, payload,
                    enqueued_at, cause_type, cause_message)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, "com.example.cargotracker.booking.infrastructure.projection",
                sequence, 0L,
                "com.example.cargotracker.shared.contract.event.HandlingActivityRegisteredEvent",
                "evt-" + System.nanoTime(), "event", "2026-09-25T01:20:00Z",
                "山田 太郎 03-0000-0000".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "2026-09-25T01:20:00Z", "org.example.Boom", causeMessage);
        return id;
    }

    private Map<String, Object> list() {
        return RestClient.create()
                .get().uri("http://localhost:" + port + "/api/v1/booking/dead-letters")
                .retrieve()
                .body(new ParameterizedTypeReference<JsonMap>() { });
    }

    @Test
    @DisplayName("止まった理由と列が読める（件数だけでは次の行動が決まらない）")
    @SuppressWarnings("unchecked")
    void listsWhatStopped() {
        String message = "値が長すぎます（" + System.nanoTime() + "）";
        park(message);

        var items = (List<Map<String, Object>>) list().get("items");

        assertThat(items).anySatisfy(item -> {
            assertThat(item.get("causeMessage")).isEqualTo(message);
            assertThat(String.valueOf(item.get("sequenceIdentifier")))
                    .as("どの列が止まったか。後続もそこで止まっている")
                    .startsWith("B-2026-0902-");
            assertThat(String.valueOf(item.get("processingGroup")))
                    .as("どの処理が止まったか。直す先はここで決まる")
                    .endsWith("infrastructure.projection");
        });
    }

    @Test
    @DisplayName("中身（payload）は返さない（退避には個人情報が載りうる・ADR-0003）")
    @SuppressWarnings("unchecked")
    void neverReturnsThePayload() {
        park("個人情報を含む退避 " + System.nanoTime());

        var items = (List<Map<String, Object>>) list().get("items");

        assertThat(items).isNotEmpty();
        assertThat(items).allSatisfy(item ->
                assertThat(item)
                        .as("鍵を破棄しても画面に平文が残る形にしない")
                        .doesNotContainKeys("payload", "metadata", "diagnostics", "token"));
    }
}
