package com.example.cargotracker.contract.roundtrip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.billing.BillingApplication;
import com.example.cargotracker.booking.BookingApplication;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/**
 * 契約イベントが実際に別サービスへ届く（IT2 引き継ぎ 1）。
 *
 * <p><b>ゴールデン JSON の一致だけでは足りません。</b> 形が同じでも、購読側が受け取れて
 * いなければ業務は動きません。IT1・IT2 ではゴールデンの一致までしか見ておらず、
 * 「往復」は 2 IT 繰り越されていました。</p>
 *
 * <p>bookingms で荷主を登録し、{@code ShipperRegisteredEvent} を billingms の
 * {@code shipper_contract_snapshot} が受け取ることを見ます。契約イベント 11 本のうち、
 * IT3 時点で発行側と購読側が両方あるのはこの 1 本だけです。</p>
 *
 * <p><b>2 つのサービスを同じ JVM で起動します。</b> そのために各サービスの
 * マイグレーションを {@code db/migration/<サービス名>/} に分けました。同じ
 * {@code classpath:db/migration} に置いたままだと、双方の V001 が衝突して起動しません。</p>
 *
 * <p>DB は分けます（Database per Service。ADR-0001 決定 1）。同じスキーマに載せると、
 * 「イベントで届いた」のか「同じ表を見ているだけ」なのかを判別できません。</p>
 */
class ContractEventRoundTripIT extends AbstractAxonIntegrationTest {

    private static ConfigurableApplicationContext booking;
    private static ConfigurableApplicationContext billing;

    /**
     * サービス固有の設定を明示して渡す。
     *
     * <p><b>2 つのサービスを同じ JVM に載せると {@code application.yml} は 1 つしか
     * 読まれません</b>（どちらが勝つかは決まっていない）。あとから起動したほうが
     * 相手のマイグレーション位置を見に行き、表が作られないまま起動します。ここで
     * 明示するのはそのためで、本番では 1 プロセス 1 サービスなので起きません。</p>
     */
    private static String[] argsFor(String service, String schema, int port) {
        return new String[] {
            "--spring.application.name=" + service + "ms",
            "--spring.flyway.locations=classpath:db/migration/" + service,
            // 相手のマッパー XML も読めるようにする。classpath: は最初に見つかった
            // 位置しか探さないので、片方のサービスのマッパーが黙って読まれなくなる。
            "--mybatis.mapper-locations=classpath*:mapper/*.xml",
            "--server.port=" + port,
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
        booking = new SpringApplicationBuilder(BookingApplication.class)
                .properties("spring.main.allow-bean-definition-overriding=true")
                .run(argsFor("booking", "roundtrip_booking", 0));
        billing = new SpringApplicationBuilder(BillingApplication.class)
                .properties("spring.main.allow-bean-definition-overriding=true")
                .run(argsFor("billing", "roundtrip_billing", 0));
    }

    @AfterAll
    static void stopBothServices() {
        if (billing != null) {
            billing.close();
        }
        if (booking != null) {
            booking.close();
        }
    }

    private static int portOf(ConfigurableApplicationContext context) {
        return Integer.parseInt(context.getEnvironment().getProperty("local.server.port", "0"));
    }

    @Test
    @DisplayName("bookingms で登録した荷主の契約が billingms に届く")
    void shipperRegisteredReachesBilling() {
        String email = "roundtrip-" + System.nanoTime() + "@example.com";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "往復商事");
        body.put("shipperType", "CORPORATE");
        body.put("email", email);
        body.put("phone", "03-0000-0000");
        body.put("address", "東京都中央区");
        body.put("contractNumber", "CT-9001");
        body.put("discountRate", "0.1500");
        body.put("acknowledgedDuplicate", false);

        ResponseEntity<Map<String, Object>> response = RestClient.create()
                .post().uri("http://localhost:" + portOf(booking) + "/api/v1/booking/shippers")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(new org.springframework.core.ParameterizedTypeReference<>() { });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String shipperId = String.valueOf(response.getBody().get("shipperId"));

        JdbcTemplate billingJdbc = billing.getBean(JdbcTemplate.class);

        // 届いたことを購読側の表で見る。発行側の表を見ても「送った」ことしか分からない。
        await("契約が billingms に届く")
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> billingJdbc.queryForObject(
                        "SELECT count(*) FROM shipper_contract_snapshot WHERE shipper_id = ?",
                        Integer.class, shipperId) == 1);

        // 中身も見る。行が増えただけでは、割引率が落ちていても緑になる。
        Map<String, Object> row = billingJdbc.queryForMap(
                "SELECT * FROM shipper_contract_snapshot WHERE shipper_id = ?", shipperId);
        assertThat(String.valueOf(row.get("contract_number"))).isEqualTo("CT-9001");
        assertThat(new java.math.BigDecimal(String.valueOf(row.get("discount_rate"))))
                .isEqualByComparingTo("0.1500");
    }

    @Test
    @DisplayName("2 つのサービスは別々の DB を見ている（同じ表を見ているのではない）")
    void servicesUseSeparateDatabases() {
        // 同じスキーマに載せていると、上の検査は「イベントで届いた」ことを判別しない。
        JdbcTemplate bookingJdbc = booking.getBean(JdbcTemplate.class);
        JdbcTemplate billingJdbc = billing.getBean(JdbcTemplate.class);

        assertThat(bookingJdbc.queryForObject("SELECT current_schema()", String.class))
                .isEqualTo("roundtrip_booking");
        assertThat(billingJdbc.queryForObject("SELECT current_schema()", String.class))
                .isEqualTo("roundtrip_billing");
    }
}
