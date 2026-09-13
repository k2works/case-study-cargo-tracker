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
            // **料率は別ファイルから名指しで取り込む**（ADR-0016）。同じ JVM に
            // 2 サービスを載せると `classpath:application.yml` は 1 つしか読まれず、
            // billingms の設定が消えて起動に失敗する（実測）。
            "--spring.config.import=optional:classpath:cargo-rates.yml",
            
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
    @DisplayName("billingms で記録した入金が bookingms に届き、予約が精算済になる（US23 §4）")
    void paymentRecordedReachesBooking() {
        var settled = settleOneBooking();

        // **購読側の表で見る。** 発行側を見ても「送った」ことしか分からない。
        await("予約が精算済になる").atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> "SETTLED".equals(booking.getBean(JdbcTemplate.class)
                        .queryForObject(
                                "SELECT booking_status FROM cargo_summary WHERE booking_id = ?",
                                String.class, settled.bookingId())));
    }

    /** 精算まで進めた予約 1 件（打ち消しの検査も同じ道具立てを使う）。 */
    private record SettledBooking(String bookingId, String invoiceId, String paymentId) {
    }

    /**
     * 引取済の予約を作り、請求書を発行して入金まで記録する。
     *
     * <p><b>画面の経路をなぞらない</b>——確かめたいのは「イベントが BC をまたいで
     * 届くか」だけである。</p>
     */
    private SettledBooking settleOneBooking() {
        // **向きが逆の 1 本目である。** IT13 までの契約は booking → tracking →
        // handling → billing の一方向で、**billing から booking へ戻るのは IT14 が
        // 最初**。往復を検査していないと、購読側が読めていないことに気づけない。
        //
        // **この検査は IT14 で赤だった。** 原因は Axon の配送ではなく、
        // **bookingms の投影に `BookingSettledEvent` の書き手が無かった**ことである
        // （IT15 T-1 で特定。TRACE ログで、reaction は受け取って `SettleBookingCommand`
        // まで通し、token も進めていた——欠けていたのは読み口だけだった）。
        // **「届かない」に見えた症状の実体は「書いていない」だった**ので、
        // 配送を疑って `@EventTag` を足すのは空振りになった。
        //
        // 計画の T7 は「契約イベントなのでゴールデン JSON **と Axon Server 経由の
        // 往復テスト**」を求めていたが、往復テストが作られていなかった。
        // ゴールデン JSON（形の固定）だけでは「届くか」は分からない。
        JdbcTemplate bookingJdbc = booking.getBean(JdbcTemplate.class);
        JdbcTemplate billingJdbc = billing.getBean(JdbcTemplate.class);

        String bookingId = "B-RT-" + System.nanoTime();
        String invoiceId = "INV-RT-" + String.valueOf(System.nanoTime()).substring(9);
        String shipperId = "SHP-RT-" + System.nanoTime();
        String trackingNumber = "TRK-RT" + String.valueOf(System.nanoTime()).substring(10);

        // 引取済の予約を作る。**画面の経路をなぞらない**——確かめたいのは
        // 「入金のイベントが BC をまたいで届くか」だけである。
        var cargos = billing.getBean(com.example.cargotracker.billing.infrastructure
                .projection.BillingCargoProjection.class);
        var shippers = billing.getBean(com.example.cargotracker.billing.infrastructure
                .projection.ShipperContractProjection.class);
        shippers.on(new com.example.cargotracker.shared.contract.event.ShipperRegisteredEvent(
                shipperId, "INDIVIDUAL", "山田 太郎", shipperId + "@example.com",
                "03-0000-0000", "東京都港区", null, null));
        cargos.on(new com.example.cargotracker.shared.contract.event.TrackingInitializedEvent(
                trackingNumber, bookingId, shipperId, "JPTYO", "JPOSA", "GENERAL",
                new java.math.BigDecimal("1200"),
                java.util.List.of(new com.example.cargotracker.shared.contract.event
                        .TrackingInitializedEvent.Leg("V-MOL-001", "JPTYO", "JPOSA",
                        java.time.Instant.parse("2026-09-01T09:00:00Z"),
                        java.time.Instant.parse("2026-09-05T18:00:00Z"))),
                java.time.Instant.parse("2026-09-01T01:00:00Z")), "evt-" + System.nanoTime());

        // 予約の側も引取済にしておく（精算は引取済からしか進まない）。
        var bookingCommands = booking.getBean(
                org.axonframework.messaging.commandhandling.gateway.CommandGateway.class);
        bookingCommands.sendAndWait(new com.example.cargotracker.booking.domain.model.commands
                .BookCargoCommand(bookingId, shipperId,
                new com.example.cargotracker.booking.domain.model.valueobjects.CargoSpecification(
                        com.example.cargotracker.booking.domain.model.valueobjects
                                .CargoType.GENERAL,
                        com.example.cargotracker.booking.domain.model.valueobjects.Weight
                                .ofKilograms("1200"),
                        new com.example.cargotracker.booking.domain.model.valueobjects.Dimensions(
                                new java.math.BigDecimal("120"), new java.math.BigDecimal("80"),
                                new java.math.BigDecimal("100")),
                        10, "往復の貨物", null, null),
                new com.example.cargotracker.booking.domain.model.valueobjects.RouteSpecification(
                        com.example.cargotracker.shared.domain.location.Location.of("JPTYO"),
                        com.example.cargotracker.shared.domain.location.Location.of("JPOSA"),
                        java.time.LocalDate.now().plusDays(60)),
                "sales01"));
        bookingCommands.sendAndWait(new com.example.cargotracker.booking.domain.model.commands
                .MarkDeliveredCommand(bookingId, trackingNumber,
                java.time.Instant.parse("2026-09-05T18:00:00Z"), "JPOSA"));

        await("予約が引取済になる").atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> "DELIVERED".equals(bookingJdbc.queryForObject(
                        "SELECT booking_status FROM cargo_summary WHERE booking_id = ?",
                        String.class, bookingId)));

        // 請求書を作り、発行して、入金を記録する。
        var billingCommands = billing.getBean(
                org.axonframework.messaging.commandhandling.gateway.CommandGateway.class);
        var calculation = billing.getBean(com.example.cargotracker.billing.application
                .InvoiceCalculation.class);
        var outcome = calculation.prepare(trackingNumber, bookingId);
        assertThat(outcome).isInstanceOf(
                com.example.cargotracker.billing.application.InvoiceCalculation.Outcome.Ready.class);
        var ready = (com.example.cargotracker.billing.application.InvoiceCalculation.Outcome.Ready)
                outcome;
        billingCommands.sendAndWait(ready.command());
        invoiceId = ready.command().invoiceId();

        final String finalInvoiceId = invoiceId;
        await("請求書が投影に入る").atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> billingJdbc.queryForObject(
                        "SELECT count(*) FROM invoice WHERE invoice_id = ?",
                        Integer.class, finalInvoiceId) == 1);

        billingCommands.sendAndWait(new com.example.cargotracker.billing.domain.model.commands
                .IssueInvoiceCommand(invoiceId, "accountant01"));
        java.math.BigDecimal total = billingJdbc.queryForObject(
                "SELECT total_amount FROM invoice WHERE invoice_id = ?",
                java.math.BigDecimal.class, invoiceId);
        String paymentId = "PAY-RT-" + System.nanoTime();
        billingCommands.sendAndWait(new com.example.cargotracker.billing.domain.model.commands
                .RecordPaymentCommand(invoiceId, paymentId, total,
                java.time.Instant.parse("2026-09-06T02:00:00Z"), "accountant01"));

        return new SettledBooking(bookingId, invoiceId, paymentId);
    }

    @Test
    @DisplayName("入金の取り消しも bookingms に届き、予約が引取済に戻る（IT15 引き継ぎ 3）")
    void paymentVoidedReachesBooking() {
        // **記録と打ち消しは同じ経路を通る。** 届く側だけ検査すると、
        // 「精算済にはなるが戻らない」形の欠陥が残る——US23 §4 でやったのと
        // 同じ見落としを、打ち消し側で繰り返さない。
        JdbcTemplate bookingJdbc = booking.getBean(JdbcTemplate.class);
        var settled = settleOneBooking();
        await("まず精算済になる").atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> "SETTLED".equals(bookingJdbc.queryForObject(
                        "SELECT booking_status FROM cargo_summary WHERE booking_id = ?",
                        String.class, settled.bookingId())));

        var billingCommands = billing.getBean(
                org.axonframework.messaging.commandhandling.gateway.CommandGateway.class);
        billingCommands.sendAndWait(new com.example.cargotracker.billing.domain.model.commands
                .VoidPaymentCommand(settled.invoiceId(), settled.paymentId(),
                "他社の入金と取り違えた", "accountant01"));

        await("予約が引取済に戻る").atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> "DELIVERED".equals(bookingJdbc.queryForObject(
                        "SELECT booking_status FROM cargo_summary WHERE booking_id = ?",
                        String.class, settled.bookingId())));
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
