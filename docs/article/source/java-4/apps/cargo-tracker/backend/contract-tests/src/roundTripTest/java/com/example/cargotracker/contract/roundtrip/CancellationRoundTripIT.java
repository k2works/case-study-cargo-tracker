package com.example.cargotracker.contract.roundtrip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.billing.BillingApplication;
import com.example.cargotracker.booking.BookingApplication;
import com.example.cargotracker.handling.HandlingApplication;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import com.example.cargotracker.tracking.TrackingApplication;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/**
 * キャンセルが 3 つの BC へ届く（UC22 / US30。IT15 T9・Try T9）。
 *
 * <p><b>ゴールデン JSON の一致だけでは足りない。</b> 形が同じでも、購読側が
 * 受け取れていなければ業務は動かない。IT14 の US23 §4 は<b>まさにその形で
 * 未達</b>だった——集約も受け入れも緑のまま、届いた先に書き手が無かった。</p>
 *
 * <p><b>1 本で 3 方向を見る。</b> {@code CargoCancelledEvent} の購読側は
 * trackingms（陸揚げ地）・handlingms（作業一覧から外す）・billingms（キャンセル料）
 * の 3 つで、<b>どれか 1 つが欠けても業務は途中で止まる</b>。別々の検査にすると、
 * 全部そろっていることを誰も確かめない。</p>
 *
 * <p><b>4 サービスを同じ JVM で起動する。</b> DB は分ける（Database per Service）
 * ——同じスキーマに載せると「イベントで届いた」のか「同じ表を見ているだけ」なのかを
 * 判別できない。</p>
 */
class CancellationRoundTripIT extends AbstractAxonIntegrationTest {

    private static ConfigurableApplicationContext booking;
    private static ConfigurableApplicationContext tracking;
    private static ConfigurableApplicationContext handling;
    private static ConfigurableApplicationContext billing;

    private static String[] argsFor(String service, String schema) {
        return new String[] {
            "--spring.application.name=" + service + "ms",
            "--spring.flyway.locations=classpath:db/migration/" + service,
            "--mybatis.mapper-locations=classpath*:mapper/*.xml",
            "--spring.config.import=optional:classpath:cargo-rates.yml",
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
    static void startServices() {
        booking = start(BookingApplication.class, "booking", "cx_booking");
        tracking = start(TrackingApplication.class, "tracking", "cx_tracking");
        handling = start(HandlingApplication.class, "handling", "cx_handling");
        billing = start(BillingApplication.class, "billing", "cx_billing");
    }

    private static ConfigurableApplicationContext start(Class<?> application, String service,
            String schema) {
        return new SpringApplicationBuilder(application)
                .properties("spring.main.allow-bean-definition-overriding=true")
                .run(argsFor(service, schema));
    }

    @AfterAll
    static void stopServices() {
        for (var context : List.of(billing, handling, tracking, booking)) {
            if (context != null) {
                context.close();
            }
        }
    }

    private static int portOf(ConfigurableApplicationContext context) {
        return Integer.parseInt(context.getEnvironment().getProperty("local.server.port", "0"));
    }

    private static final RestClient REST = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    @SuppressWarnings("unchecked")
    private static Map<String, Object> post(ConfigurableApplicationContext service,
            String path, Map<String, Object> body, String username) {
        return REST.post().uri("http://localhost:" + portOf(service) + path)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Auth-Username", username)
                .body(body)
                .retrieve().body(Map.class);
    }

    @Test
    @DisplayName("承認したキャンセルが追跡・荷役・請求の 3 つに届く")
    void cancellationReachesEverySubscriber() {
        JdbcTemplate bookingJdbc = booking.getBean(JdbcTemplate.class);
        JdbcTemplate trackingJdbc = tracking.getBean(JdbcTemplate.class);
        JdbcTemplate handlingJdbc = handling.getBean(JdbcTemplate.class);
        JdbcTemplate billingJdbc = billing.getBean(JdbcTemplate.class);

        String bookingId = inTransitBooking(bookingJdbc);
        String trackingNumber = bookingJdbc.queryForObject(
                "SELECT tracking_number FROM cargo_summary WHERE booking_id = ?",
                String.class, bookingId);

        post(booking, "/api/v1/booking/bookings/" + bookingId + "/cancellation",
                Map.of("reason", "荷主の発注取消"), "sales01");
        await("承認待ちに出る").atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> bookingJdbc.queryForObject(
                        "SELECT count(*) FROM cancellation_request "
                        + "WHERE booking_id = ? AND decision IS NULL",
                        Integer.class, bookingId) == 1);

        post(booking, "/api/v1/booking/bookings/" + bookingId + "/cancellation/approval",
                Map.of("dischargeUnLocode", "SGSIN"), "tracker01");

        // **購読側の表で見る。** 発行側を見ても「送った」ことしか分からない。
        await("trackingms が陸揚げ地を記録する").atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> "SGSIN".equals(trackingJdbc.queryForObject(
                        "SELECT cancellation_discharge_unlocode FROM tracking_summary "
                        + "WHERE tracking_number = ?", String.class, trackingNumber)));
        assertThat(trackingJdbc.queryForObject(
                "SELECT closed FROM tracking_summary WHERE tracking_number = ?",
                Boolean.class, trackingNumber))
                .as("**承認しても追跡は閉じない**（陸揚げの荷役をこれから記録する）")
                .isFalse();

        await("handlingms が作業一覧から外す").atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> Boolean.TRUE.equals(handlingJdbc.queryForObject(
                        "SELECT cancelled FROM cargo_snapshot WHERE booking_id = ?",
                        Boolean.class, bookingId)));

        await("billingms がキャンセル料を積む").atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> billingJdbc.queryForObject(
                        "SELECT count(*) FROM invoice_line_item li "
                        + "JOIN invoice i ON i.invoice_id = li.invoice_id "
                        + "WHERE i.booking_id = ? AND li.item_type = 'CANCELLATION_FEE'",
                        Integer.class, bookingId) == 1);

        // **陸揚げの荷降しで初めて閉じる**（不変条件 9）。
        // 積込を挟む——受領の次に荷降しは来ない（遷移表）。**キャンセルした貨物にも
        // 荷役は記録できる**（作業一覧から外れるだけで、降ろす作業は残っている）。
        post(handling, "/api/v1/handling/activities", Map.of(
                "activityId", "act-load-" + System.nanoTime(),
                "trackingNumber", trackingNumber,
                "handlingType", "LOAD",
                "voyageNumber", "V-CX-001",
                "unLocode", "JPTYO"), "handler01");
        await("積込が追跡に届く").atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> "LOADED".equals(trackingJdbc.queryForObject(
                        "SELECT transport_status FROM tracking_summary "
                        + "WHERE tracking_number = ?", String.class, trackingNumber)));

        post(handling, "/api/v1/handling/activities", Map.of(
                "activityId", "act-cx-" + System.nanoTime(),
                "trackingNumber", trackingNumber,
                "handlingType", "UNLOAD",
                "voyageNumber", "V-CX-001",
                "unLocode", "SGSIN"), "handler01");
        await("陸揚げの荷降しで追跡が閉じる").atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> Boolean.TRUE.equals(trackingJdbc.queryForObject(
                        "SELECT closed FROM tracking_summary WHERE tracking_number = ?",
                        Boolean.class, trackingNumber)));
    }

    /**
     * 輸送中の予約を 1 件作る。
     *
     * <p><b>画面の経路をそのまま通す。</b> 裏口を置かない——確かめたいのは
     * キャンセルの配送だが、そこへ至る道が本番と違うと、本番で起きることを
     * 見たことにならない。</p>
     */
    private String inTransitBooking(JdbcTemplate bookingJdbc) {
        String suffix = String.valueOf(System.nanoTime());
        Map<String, Object> shipper = new LinkedHashMap<>();
        shipper.put("name", "取消商事");
        shipper.put("shipperType", "INDIVIDUAL");
        shipper.put("email", "cx-" + suffix + "@example.com");
        shipper.put("phone", "03-0000-0000");
        shipper.put("address", "東京都中央区");
        shipper.put("acknowledgedDuplicate", false);
        String shipperId = String.valueOf(
                post(booking, "/api/v1/booking/shippers", shipper, "sales01").get("shipperId"));

        Map<String, Object> cargo = new LinkedHashMap<>();
        cargo.put("shipperId", shipperId);
        cargo.put("originUnLocode", "JPTYO");
        cargo.put("destinationUnLocode", "USNYC");
        cargo.put("arrivalDeadline", java.time.LocalDate.now().plusDays(90).toString());
        cargo.put("cargoType", "GENERAL");
        cargo.put("weightKg", "1200");
        cargo.put("lengthCm", "120");
        cargo.put("widthCm", "80");
        cargo.put("heightCm", "100");
        cargo.put("quantity", 10);
        cargo.put("productName", "止める貨物 " + suffix);
        String bookingId = String.valueOf(
                post(booking, "/api/v1/booking/bookings", cargo, "sales01").get("bookingId"));

        post(booking, "/api/v1/booking/bookings/" + bookingId + "/routing-request",
                Map.of(), "sales01");
        Map<String, Object> leg = new LinkedHashMap<>();
        leg.put("voyageNumber", "V-CX-001");
        leg.put("loadUnLocode", "JPTYO");
        leg.put("unloadUnLocode", "SGSIN");
        leg.put("loadTime", java.time.Instant.now().plusSeconds(86_400).toString());
        leg.put("unloadTime", java.time.Instant.now().plusSeconds(864_000).toString());
        Map<String, Object> leg2 = new LinkedHashMap<>();
        leg2.put("voyageNumber", "V-CX-002");
        leg2.put("loadUnLocode", "SGSIN");
        leg2.put("unloadUnLocode", "USNYC");
        leg2.put("loadTime", java.time.Instant.now().plusSeconds(950_000).toString());
        leg2.put("unloadTime", java.time.Instant.now().plusSeconds(1_900_000).toString());
        post(booking, "/api/v1/booking/bookings/" + bookingId + "/route",
                Map.of("legs", List.of(leg, leg2)), "routing01");
        post(booking, "/api/v1/booking/bookings/" + bookingId + "/notifications",
                Map.of("recipientEmail", "shipper@example.com", "summary", "JPTYO → USNYC"),
                "sales01");
        post(booking, "/api/v1/booking/bookings/" + bookingId + "/confirmation",
                Map.of(), "sales01");
        post(booking, "/api/v1/booking/bookings/" + bookingId + "/tracking-number",
                Map.of(), "routing01");
        await("追跡番号が付く").atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> bookingJdbc.queryForObject(
                        "SELECT count(*) FROM cargo_summary "
                        + "WHERE booking_id = ? AND tracking_number IS NOT NULL",
                        Integer.class, bookingId) == 1);

        String trackingNumber = bookingJdbc.queryForObject(
                "SELECT tracking_number FROM cargo_summary WHERE booking_id = ?",
                String.class, bookingId);
        // 貨物の写しが handlingms へ届くまで待つ（荷役はそれからでないと通らない）。
        JdbcTemplate handlingJdbc = handling.getBean(JdbcTemplate.class);
        await("貨物の写しが handlingms へ届く").atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> handlingJdbc.queryForObject(
                        "SELECT count(*) FROM cargo_snapshot WHERE tracking_number = ?",
                        Integer.class, trackingNumber) == 1);

        post(handling, "/api/v1/handling/activities", Map.of(
                "activityId", "act-rx-" + suffix,
                "trackingNumber", trackingNumber,
                "handlingType", "RECEIVE",
                "unLocode", "JPTYO"), "handler01");
        await("予約が輸送中になる").atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> "IN_TRANSIT".equals(bookingJdbc.queryForObject(
                        "SELECT booking_status FROM cargo_summary WHERE booking_id = ?",
                        String.class, bookingId)));
        return bookingId;
    }
}
