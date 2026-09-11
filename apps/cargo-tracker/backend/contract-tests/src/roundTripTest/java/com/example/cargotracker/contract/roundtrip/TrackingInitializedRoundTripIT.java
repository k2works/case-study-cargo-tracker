package com.example.cargotracker.contract.roundtrip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.billing.BillingApplication;
import com.example.cargotracker.shared.contract.command.InitializeTrackingCommand;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import com.example.cargotracker.tracking.TrackingApplication;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 追跡の開始が請求に届く（IT13 / US21）。
 *
 * <p><b>契約に項目を足したら、届くことまで見る。</b> ゴールデン JSON は「いまの形」を
 * 固定するだけで、<b>実際に相手のサービスへ運ばれるか</b>は判別しません。IT13 で
 * {@code TrackingInitializedEvent} に重量を足した目的は billingms が料金を数えられる
 * ことなので、**重量が向こう側の表に入る**ところまで確かめます（開発戦略の終盤
 * Phase 2 が、契約を足す IT に課している形）。</p>
 *
 * <p><b>2 サービスを同じ JVM に載せる都合</b>は {@code ContractEventRoundTripIT} と
 * 同じです（マイグレーション位置とマッパーの位置を明示する）。</p>
 */
class TrackingInitializedRoundTripIT extends AbstractAxonIntegrationTest {

    private static ConfigurableApplicationContext tracking;
    private static ConfigurableApplicationContext billing;

    private static String[] argsFor(String service, String schema) {
        return new String[] {
            "--spring.application.name=" + service + "ms",
            "--spring.flyway.locations=classpath:db/migration/" + service,
            "--mybatis.mapper-locations=classpath*:mapper/*.xml",
            "--server.port=0",
            "--axon.axonserver.servers=" + AXON_SERVER.getAxonServerAddress(),
            "--spring.datasource.url=" + POSTGRES.getJdbcUrl() + "&currentSchema=" + schema,
            "--spring.datasource.username=" + POSTGRES.getUsername(),
            "--spring.datasource.password=" + POSTGRES.getPassword(),
            "--spring.flyway.schemas=" + schema,
            "--spring.flyway.default-schema=" + schema,
            "--spring.flyway.create-schemas=true",
        };
    }

    @BeforeAll
    static void startBothServices() {
        tracking = new SpringApplicationBuilder(TrackingApplication.class)
                .properties("spring.main.allow-bean-definition-overriding=true")
                .run(argsFor("tracking", "roundtrip_tracking"));
        billing = new SpringApplicationBuilder(BillingApplication.class)
                .properties("spring.main.allow-bean-definition-overriding=true")
                .run(argsFor("billing", "roundtrip_billing_cargo"));
    }

    @AfterAll
    static void stopBothServices() {
        if (billing != null) {
            billing.close();
        }
        if (tracking != null) {
            tracking.close();
        }
    }

    @Test
    @DisplayName("追跡の開始で、区間と重量が billingms の貨物スナップショットに届く")
    void trackingInitializedReachesBillingWithWeight() {
        String trackingNumber = "TRK-RT" + System.nanoTime() % 100000000L;
        Instant issuedAt = Instant.parse("2026-09-08T01:00:00Z");

        tracking.getBean(CommandGateway.class).sendAndWait(new InitializeTrackingCommand(
                trackingNumber, "b-rt-" + System.nanoTime(), "SHP-000001", "JPTYO", "USNYC",
                "GENERAL", new BigDecimal("1200"),
                List.of(new InitializeTrackingCommand.LegDto("V-MOL-001", "JPTYO", "SGSIN",
                                issuedAt.plusSeconds(86_400), issuedAt.plusSeconds(600_000)),
                        new InitializeTrackingCommand.LegDto("V-ONE-002", "SGSIN", "USNYC",
                                issuedAt.plusSeconds(700_000), issuedAt.plusSeconds(1_400_000))),
                issuedAt), String.class);

        JdbcTemplate billingJdbc = billing.getBean(JdbcTemplate.class);

        // 届いたことを購読側の表で見る。発行側を見ても「送った」ことしか分からない。
        await("貨物スナップショットが billingms に届く")
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> billingJdbc.queryForObject(
                        "SELECT count(*) FROM billing_cargo_snapshot WHERE tracking_number = ?",
                        Integer.class, trackingNumber) == 1);

        // **中身も見る。** 行が増えただけでは、足した重量が落ちていても緑になる。
        Map<String, Object> row = billingJdbc.queryForMap(
                "SELECT weight_kg, cargo_type, destination_unlocode FROM billing_cargo_snapshot "
                        + "WHERE tracking_number = ?", trackingNumber);
        assertThat(new BigDecimal(String.valueOf(row.get("weight_kg"))))
                .as("重量を足した目的は請求が数えられることなので、落ちていては意味がない")
                .isEqualByComparingTo("1200");
        assertThat(row.get("cargo_type")).isEqualTo("GENERAL");
        assertThat(row.get("destination_unlocode")).isEqualTo("USNYC");

        // 区間も順に届く（地域係数は区間ごとに数える）。
        assertThat(billingJdbc.queryForList(
                "SELECT unload_unlocode FROM billing_cargo_leg WHERE tracking_number = ? "
                        + "ORDER BY leg_seq", String.class, trackingNumber))
                .containsExactly("SGSIN", "USNYC");
    }
}
