package com.example.cargotracker.tracking.infrastructure.projection;

import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
import com.example.cargotracker.shared.infrastructure.axon.DeadLetterRetryEndpoint;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.axonframework.messaging.eventhandling.gateway.EventGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.axonframework.test.server.AxonServerContainer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 書けなかったイベントを退避する（引き継ぎ枠 A・[ADR-0014]）。
 *
 * <p><b>IT11 で実際に起きた事象を、そのままの形で再現する。</b> 誤配の自動起票が
 * {@code exception_id VARCHAR(36)} に 40 文字を入れようとして落ち、その 1 件で
 * Event Processor が止まった。層ごとの検査はすべて緑のままで、クラスタ E2E だけが
 * 気づいた。ここでは同じ壊れ方を同じ原因（桁あふれ）で起こす——
 * {@code tracking_summary.tracking_number} は {@code VARCHAR(25)} なので、
 * それより長い追跡番号を持つ開始イベントは投影で必ず落ちる。</p>
 *
 * <p><b>投影のハンドラを直接呼ばない。</b> {@code TrackingProjectionIT} は
 * {@code projection.on(...)} を呼ぶので、処理が止まるかどうかを判別しない。
 * 止まり方を見るには、イベントストアに載せて Processor に運ばせる必要がある。</p>
 *
 * <p><b>コンテナはこのテスト専用に立てる。</b> 共有の Axon Server に毒を流すと、
 * <b>他のテストクラスの投影まで巻き添えで止まる</b>——スキーマは分けてあるが
 * イベントストアは 1 つで、どの Processor もその毒を読む（実測。TrackingControllerIT
 * が 9 件まとめて落ちた）。{@code AxonServerOutageIT} と同じ理由・同じ形である。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DeadLetterQueueIT {

    // 組み立ては AbstractAxonIntegrationTest に集める。同じ内容を各テストで書くと、
    // 起動猶予のような設定を片方だけ直すことになる（IT2 で実際に起きた）。
    static final AxonServerContainer AXON_SERVER =
            AbstractAxonIntegrationTest.axonServerContainer();

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        AXON_SERVER.start();
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("axon.axonserver.servers", AXON_SERVER::getAxonServerAddress);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final Instant AT = Instant.parse("2026-09-09T01:00:00Z");

    /** {@code tracking_summary.tracking_number} は VARCHAR(25)。26 文字は必ず落ちる。 */
    private static final String TOO_LONG_TRACKING_NUMBER = "T-DLQ-POISON-0123456789012";

    @Autowired
    private EventGateway events;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DeadLetterRetryEndpoint retry;

    private static TrackingInitializedEvent initialized(String trackingNumber) {
        return new TrackingInitializedEvent(trackingNumber, "b-" + System.nanoTime(),
                "SHP-000001", "JPTYO", "USNYC", "GENERAL",
                new BigDecimal("1200"),
                List.of(new TrackingInitializedEvent.Leg("V-MOL-001", "JPTYO", "USNYC",
                        Instant.parse("2026-09-10T09:00:00Z"),
                        Instant.parse("2026-09-24T18:00:00Z"))),
                AT);
    }

    @Test
    @DisplayName("投影が書けないイベントは退避され、原因つきで残る（黙って捨てない）")
    void unwritableEventIsParkedWithItsCause() {
        assertThat(TOO_LONG_TRACKING_NUMBER).hasSizeGreaterThan(25);

        events.publish(List.of(initialized(TOO_LONG_TRACKING_NUMBER))).join();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            List<Map<String, Object>> parked = jdbc.queryForList(
                    "SELECT processing_group, cause_type, cause_message FROM dead_letter_entry");
            assertThat(parked)
                    .as("退避先が無いと、この 1 件で Processor が止まり続ける（IT11）")
                    .isNotEmpty();
            assertThat(parked.getFirst())
                    .as("なぜ書けなかったかが残らないと、直しようがない")
                    .hasEntrySatisfying("processing_group", group ->
                            assertThat((String) group)
                                    .contains("com.example.cargotracker.tracking"
                                            + ".infrastructure.projection"));
            assertThat((String) parked.getFirst().get("cause_message"))
                    .contains("value too long");
        });
    }

    @Test
    @DisplayName("退避したあとも Processor は動き続け、次のイベントを引き受ける")
    void theProcessorKeepsWorkingAfterParking() {
        // **止まると、次のイベントは誰にも読まれない。** IT11 はこれが起きて、
        // 後続が Event Store に積まれたまま届かなくなった。退避先があると、
        // Processor は退避して次へ進むので、2 件目も引き受けられる。
        //
        // 2 件目も書けないもので確かめる。列は貨物ごとに分けたので（IT13）、
        // 書けるもので確かめても通るが、それでは「退避したあとも動き続けるか」
        // ではなく「別の列だから届いた」を見ることになる。**同じ壊れ方の 2 件目**を
        // 引き受けられることが、ここで見たいことである。
        events.publish(List.of(initialized(TOO_LONG_TRACKING_NUMBER + "-A"))).join();
        events.publish(List.of(initialized(TOO_LONG_TRACKING_NUMBER + "-B"))).join();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM dead_letter_entry", Integer.class))
                        .as("1 件目で止まっているなら、2 件目は退避先にも現れない")
                        .isGreaterThanOrEqualTo(2));
    }

    @Test
    @DisplayName("処理の列は貨物ごとに分かれる（別の貨物の毒で巻き添えにならない）")
    void anotherCargoIsNotParkedByAPoisonEvent() {
        // **列が全体で 1 本だと、1 件の毒で無関係の貨物まで退避される。** IT12 の
        // クラスタ E2E で実測した——4 件のうち 3 件が巻き添えだった。退避先は
        // 「同じ列の後続」を意図的に退避するので（順序を守るため）、列の切り方が
        // そのまま被害の範囲になる。貨物ごとに切れていれば、毒とは別の貨物の
        // イベントは**そのまま投影される**。
        events.publish(List.of(initialized(TOO_LONG_TRACKING_NUMBER + "-C"))).join();

        String healthy = "TRK-DLQ-OK-" + System.nanoTime() % 1000000L;
        events.publish(List.of(initialized(healthy))).join();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM tracking_summary WHERE tracking_number = ?",
                        Integer.class, healthy))
                        .as("毒と同じ列に入れられると、書けるはずのこの貨物も退避される")
                        .isEqualTo(1));
    }

    @Test
    @DisplayName("原因を直してから処理し直すと、退避したイベントが反映される（消さない）")
    void parkedEventIsReprocessedAfterTheCauseIsFixed() {
        // **直したあとに退避を消すのは「黙って捨てる」こと**で、[ADR-0014] 決定 1 に
        // 反する。IT12 の実機確認では処理し直す入口が無く、実際に DELETE で片づけた。
        // ここでは同じ道筋をたどる——毒を流して退避させ、**原因（列の桁）を直し**、
        // 入口を呼ぶと反映される。
        String parked = TOO_LONG_TRACKING_NUMBER + "-R";
        events.publish(List.of(initialized(parked))).join();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(jdbc.queryForList(
                        "SELECT sequence_identifier FROM dead_letter_entry "
                        + "WHERE sequence_identifier = ?", parked))
                        .as("退避されていなければ、処理し直すものが無い")
                        .isNotEmpty());

        // 原因を直す（本番なら投影のコードを直して入れ替える。ここでは桁を広げる）。
        //
        // **投影が書く先は 1 つではない。** 旅程も同じ桁で持っているので、片方だけ
        // 直すと処理し直しても通らない（実測。retry は 0 列を返す）。
        widenTrackingNumberTo(60);
        try {
            Map<String, Object> result = retry.retry();

            assertThat(result).as("処理し直した列の数を返す（何も起きなかったのか、が分かる）")
                    .containsKey("processedSequences");
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM tracking_summary WHERE tracking_number = ?",
                    Integer.class, parked))
                    .as("処理し直したのに反映されないなら、退避先から取り出せていない")
                    .isEqualTo(1);
        } finally {
            // **桁を戻す。** 戻さないと、同じ DB を使う他の検査の毒が毒でなくなり、
            // 実行順で結果が変わる（実測。1 件目の検査が落ちた）。
            jdbc.update("DELETE FROM tracking_leg WHERE length(tracking_number) > 25");
            jdbc.update("DELETE FROM tracking_summary WHERE length(tracking_number) > 25");
            widenTrackingNumberTo(25);
        }
    }

    private void widenTrackingNumberTo(int size) {
        jdbc.execute("ALTER TABLE tracking_summary "
                + "ALTER COLUMN tracking_number TYPE VARCHAR(" + size + ")");
        jdbc.execute("ALTER TABLE tracking_leg "
                + "ALTER COLUMN tracking_number TYPE VARCHAR(" + size + ")");
    }
}
