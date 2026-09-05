package com.example.cargotracker.contract.roundtrip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.booking.BookingApplication;
import com.example.cargotracker.routing.RoutingApplication;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * 契約クエリが実際に別サービスへ届く（US08 / IT5）。
 *
 * <p><b>ゴールデン JSON の一致だけでは足りません。</b> 形が同じでも、Query Bus に
 * ハンドラが登録されていなければ届きません。届かないときは
 * {@code NoHandlerForQueryException} になり、bookingms は 503 を返します。
 * 「届く」ことは、形の一致とは別の検査です。</p>
 *
 * <p>DB は分けます（Database per Service）。同じスキーマに載せると、
 * 「クエリで届いた」のか「同じ表を見ているだけ」なのかを判別できません。</p>
 */
class ContractQueryRoundTripIT extends AbstractAxonIntegrationTest {

    /** 業務タイムゾーン。期限は日付なので、どの時間帯で日付にするかが結果を変える。 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");

    private static ConfigurableApplicationContext booking;
    private static ConfigurableApplicationContext routing;

    private static String[] argsFor(String service, String schema, int port) {
        return new String[] {
            "--spring.application.name=" + service + "ms",
            "--spring.flyway.locations=classpath:db/migration/" + service,
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
                .run(argsFor("booking", "query_roundtrip_booking", 0));
        routing = new SpringApplicationBuilder(RoutingApplication.class)
                .properties("spring.main.allow-bean-definition-overriding=true")
                .run(argsFor("routing", "query_roundtrip_routing", 0));
    }

    @AfterAll
    static void stopBothServices() {
        if (routing != null) {
            routing.close();
        }
        if (booking != null) {
            booking.close();
        }
    }

    private static int portOf(ConfigurableApplicationContext context) {
        return Integer.parseInt(context.getEnvironment().getProperty("local.server.port", "0"));
    }

    private static String urlOf(ConfigurableApplicationContext context, String path) {
        return "http://localhost:" + portOf(context) + path;
    }

    private static final ParameterizedTypeReference<Map<String, Object>> MAP =
            new ParameterizedTypeReference<>() { };

    /** 業務タイムゾーンの日時を ISO で。UTC で作ると、時差の分だけ日付がずれる。 */
    private static String at(int daysFromNow, int hour) {
        return ZonedDateTime.now(ZONE).plusDays(daysFromNow)
                .withHour(hour).withMinute(0).withSecond(0).withNano(0)
                .format(DateTimeFormatter.ISO_INSTANT);
    }

    private static String today(int daysFromNow) {
        return LocalDate.now(ZONE).plusDays(daysFromNow).toString();
    }

    @Test
    @DisplayName("bookingms から問い合わせた経路候補が routingms から返る")
    void routeCandidatesReachRoutingAndComeBack() {
        // routingms に航海を登録する。
        String voyageNumber = "V-RT-" + Long.toString(System.nanoTime(), 36);
        Map<String, Object> voyage = new LinkedHashMap<>();
        voyage.put("voyageNumber", voyageNumber);
        voyage.put("carrierCode", "MOL");
        voyage.put("carrierName", "商船三井");
        voyage.put("vesselName", "MOL EXPRESS");
        voyage.put("movements", List.of(Map.of(
                "departureUnLocode", "JPTYO",
                "arrivalUnLocode", "USNYC",
                "departureAt", at(2, 9),
                "arrivalAt", at(16, 18))));
        voyage.put("acceptedCargoTypes", List.of("GENERAL"));

        assertThat(RestClient.create()
                .post().uri(urlOf(routing, "/api/v1/routing/voyages"))
                .contentType(MediaType.APPLICATION_JSON).body(voyage)
                .retrieve().toEntity(MAP).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        // bookingms に荷主と予約を作る。
        String shipperId = registerShipper();
        String bookingId = bookCargo(shipperId);

        // 投影が入るまで待つ。入る前に問い合わせると 202 が返る。
        await("予約の投影が入る").atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> RestClient.create()
                        .get().uri(urlOf(booking, "/api/v1/booking/bookings/" + bookingId))
                        .retrieve().toEntity(MAP).getStatusCode() == HttpStatus.OK);

        // ここが往復。bookingms の Controller → ACL → Query Bus → routingms。
        await("経路候補が返る").atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    ResponseEntity<Map<String, Object>> response = RestClient.create()
                            .get().uri(urlOf(booking, "/api/v1/booking/bookings/" + bookingId
                                    + "/route-candidates"))
                            .retrieve().toEntity(MAP);

                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> candidates =
                            (List<Map<String, Object>>) response.getBody().get("candidates");
                    // 形だけでなく、登録した航海がそのまま返ることを見る。
                    assertThat(candidates)
                            .as("登録した航海が候補に出る（届いていないと 0 件になる）")
                            .anySatisfy(candidate -> {
                                @SuppressWarnings("unchecked")
                                List<Map<String, Object>> legs =
                                        (List<Map<String, Object>>) candidate.get("legs");
                                assertThat(legs).hasSize(1);
                                assertThat(legs.get(0).get("voyageNumber"))
                                        .isEqualTo(voyageNumber);
                                assertThat(candidate.get("direct")).isEqualTo(true);
                            });
                });
    }

    @Test
    @DisplayName("2 つのサービスは別々の DB を見ている")
    void servicesUseSeparateDatabases() {
        var bookingJdbc = booking.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        var routingJdbc = routing.getBean(org.springframework.jdbc.core.JdbcTemplate.class);

        assertThat(bookingJdbc.queryForObject("SELECT current_schema()", String.class))
                .isEqualTo("query_roundtrip_booking");
        assertThat(routingJdbc.queryForObject("SELECT current_schema()", String.class))
                .isEqualTo("query_roundtrip_routing");
    }

    private String registerShipper() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "経路商事");
        body.put("shipperType", "INDIVIDUAL");
        body.put("email", "route-" + System.nanoTime() + "@example.com");
        body.put("phone", "03-0000-0000");
        body.put("address", "東京都中央区");
        body.put("acknowledgedDuplicate", false);
        return String.valueOf(RestClient.create()
                .post().uri(urlOf(booking, "/api/v1/booking/shippers"))
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().toEntity(MAP).getBody().get("shipperId"));
    }

    private String bookCargo(String shipperId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("shipperId", shipperId);
        body.put("originUnLocode", "JPTYO");
        body.put("destinationUnLocode", "USNYC");
        body.put("arrivalDeadline", today(30));
        body.put("cargoType", "GENERAL");
        body.put("weightKg", "1200.00");
        body.put("lengthCm", "120.00");
        body.put("widthCm", "80.00");
        body.put("heightCm", "100.00");
        body.put("quantity", 10);
        body.put("productName", "自動車部品");
        return String.valueOf(RestClient.create()
                .post().uri(urlOf(booking, "/api/v1/booking/bookings"))
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().toEntity(MAP).getBody().get("bookingId"));
    }
}
