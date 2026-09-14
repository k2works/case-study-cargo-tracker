package com.example.cargotracker.contract.roundtrip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.billing.BillingApplication;
import com.example.cargotracker.booking.BookingApplication;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.time.Duration;
import java.time.LocalDate;
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
 * シミュレーション由来の印が読み口まで届く（US33 §受入基準 3 / IT16 T11）。
 *
 * <p><b>印を付けただけでは業務の一覧から外れない。</b> 印は
 * {@code ShipperRegisteredEvent} に載るが、外すのは<b>各 BC の読み口の側</b>で
 * ある（注 N7）。載せる側だけを見ても「送った」ことしか分からない。</p>
 *
 * <p><b>1 本で 4 つの読み口を見る。</b> 荷主一覧・予約一覧（bookingms）と
 * 荷主の写し・請求一覧（billingms）で、<b>どれか 1 つが欠けても
 * シミュレーションの貨物が業務の一覧に紛れる</b>。別々の検査にすると、
 * 全部そろっていることを誰も確かめない。</p>
 *
 * <p><b>印は荷主から引き継がれる。</b> 予約も請求も荷主の印を辿るので、
 * 「荷主だけ外れて貨物が残る」形になっていないことを、同じ検査で押さえる。</p>
 */
class SimulatedOriginRoundTripIT extends AbstractAxonIntegrationTest {

    private static ConfigurableApplicationContext booking;
    private static ConfigurableApplicationContext billing;

    private static final RestClient REST = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

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
        booking = start(BookingApplication.class, "booking", "sim_booking");
        billing = start(BillingApplication.class, "billing", "sim_billing");
    }

    private static ConfigurableApplicationContext start(Class<?> application, String service,
            String schema) {
        return new SpringApplicationBuilder(application)
                .properties("spring.main.allow-bean-definition-overriding=true")
                .run(argsFor(service, schema));
    }

    @AfterAll
    static void stopServices() {
        for (var context : List.of(billing, booking)) {
            if (context != null) {
                context.close();
            }
        }
    }

    private static int portOf(ConfigurableApplicationContext context) {
        return Integer.parseInt(context.getEnvironment().getProperty("local.server.port", "0"));
    }

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
    @DisplayName("US33 §3: シミュレーションが作った荷主と貨物は、業務の一覧から外れる")
    void simulatedOriginReachesEveryReadModel() {
        JdbcTemplate bookingJdbc = booking.getBean(JdbcTemplate.class);
        JdbcTemplate billingJdbc = billing.getBean(JdbcTemplate.class);

        String suffix = String.valueOf(System.nanoTime());
        String simulatedShipper = registerShipper(suffix + "-sim", true);
        String realShipper = registerShipper(suffix + "-real", false);

        // (1) 荷主一覧（bookingms）。**印が投影まで届く。**
        await("荷主の投影に印が届く").atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> Boolean.TRUE.equals(bookingJdbc.queryForObject(
                        "SELECT simulated FROM shipper WHERE shipper_id = ?",
                        Boolean.class, simulatedShipper)));
        assertThat(bookingJdbc.queryForObject(
                "SELECT simulated FROM shipper WHERE shipper_id = ?",
                Boolean.class, realShipper))
                .as("**印を付けていない荷主は本物。** 既定を「シミュレーション」にしない")
                .isFalse();

        // (2) 荷主の写し（billingms）。**BC をまたいで届く**——ここが欠けると、
        // 請求側は誰がシミュレーションかを知らないまま請求書を並べる。
        await("荷主の写しに印が届く").atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> Boolean.TRUE.equals(billingJdbc.queryForObject(
                        "SELECT simulated FROM shipper_contract_snapshot WHERE shipper_id = ?",
                        Boolean.class, simulatedShipper)));

        String simulatedBooking = bookCargo(simulatedShipper, "試験貨物 " + suffix);
        String realBooking = bookCargo(realShipper, "本物の貨物 " + suffix);

        // (3) 予約一覧（bookingms）。**印は荷主から引き継ぐ**——予約の登録は
        // 印を送らないので、投影が荷主を辿れていなければここで落ちる。
        await("貨物の投影が荷主の印を引き継ぐ").atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> Boolean.TRUE.equals(bookingJdbc.queryForObject(
                        "SELECT simulated FROM cargo_summary WHERE booking_id = ?",
                        Boolean.class, simulatedBooking)));
        assertThat(bookingJdbc.queryForObject(
                "SELECT simulated FROM cargo_summary WHERE booking_id = ?",
                Boolean.class, realBooking))
                .isFalse();

        // (4) 予約一覧の読み口。**除外は読み口の側にある**（注 N7）。
        // 表を数えても「一覧に出ない」ことは分からない——一覧が使う経路で見る。
        await("本物の予約が一覧に出る").atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> bookingIds().contains(realBooking));
        assertThat(bookingIds())
                .as("**シミュレーションが作った貨物は業務の予約一覧に出ない**")
                .doesNotContain(simulatedBooking);
    }

    /** 予約一覧の読み口が返す予約 ID。<b>表ではなく一覧の経路で見る</b>。 */
    @SuppressWarnings("unchecked")
    private List<String> bookingIds() {
        Map<String, Object> body = REST.get()
                .uri("http://localhost:" + portOf(booking)
                        + "/api/v1/booking/bookings?page=0&size=200")
                .header("X-Auth-Username", "sales01")
                .retrieve().body(Map.class);
        List<Map<String, Object>> items = body == null
                ? List.of() : (List<Map<String, Object>>) body.getOrDefault("items", List.of());
        return items.stream().map(item -> String.valueOf(item.get("bookingId"))).toList();
    }

    private String registerShipper(String suffix, boolean simulated) {
        Map<String, Object> shipper = new LinkedHashMap<>();
        shipper.put("name", (simulated ? "シミュレーション商事 " : "本物商事 ") + suffix);
        shipper.put("shipperType", "INDIVIDUAL");
        shipper.put("email", "sim-" + suffix + "@example.com");
        shipper.put("phone", "03-0000-0000");
        shipper.put("address", "東京都中央区");
        shipper.put("acknowledgedDuplicate", false);
        shipper.put("simulated", simulated);
        return String.valueOf(
                post(booking, "/api/v1/booking/shippers", shipper, "sales01").get("shipperId"));
    }

    /**
     * 予約を 1 件作る。
     *
     * <p><b>画面の経路をそのまま通す。</b> 裏口を置くと、そこへ至る道が本番と
     * 違い、本番で起きることを見たことにならない。</p>
     */
    private String bookCargo(String shipperId, String productName) {
        Map<String, Object> cargo = new LinkedHashMap<>();
        cargo.put("shipperId", shipperId);
        cargo.put("originUnLocode", "JPTYO");
        cargo.put("destinationUnLocode", "USNYC");
        cargo.put("arrivalDeadline", LocalDate.now().plusDays(90).toString());
        cargo.put("cargoType", "GENERAL");
        cargo.put("weightKg", "1200");
        cargo.put("lengthCm", "120");
        cargo.put("widthCm", "80");
        cargo.put("heightCm", "100");
        cargo.put("quantity", 10);
        cargo.put("productName", productName);
        return String.valueOf(
                post(booking, "/api/v1/booking/bookings", cargo, "sales01").get("bookingId"));
    }
}
