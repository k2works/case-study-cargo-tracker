package com.example.cargotracker.booking.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.booking.application.port.RouteCandidateFinder;
import com.example.cargotracker.booking.domain.model.valueobjects.Leg;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteCandidate;
import com.example.cargotracker.shared.domain.location.Location;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.client.RestClient;

/**
 * 経路候補の REST を HTTP 越しに固定する（US08）。
 *
 * <p><b>この層が空いていた。</b> 「落ちているときは 503」は ACL の単体（例外を投げる
 * ところまで）とフロントのモック（自分で 503 を作って渡す）に分かれていて、
 * つなぎ目である {@code ApiExceptionHandler} を誰も踏んでいなかった。ハンドラを
 * 外しても両方緑のままになる（IT5 レビュー 中 4）。</p>
 *
 * <p>応答の形も固定する。フロントのモックが前提にしている形（{@code candidates[]
 * .legs[].loadUnLocode} / {@code transitDays} / {@code direct} / {@code truncated}）が
 * 本物と違っていても、フロントのテストだけでは気づけない。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RouteCandidateEndpointIT extends AbstractAxonIntegrationTest {

    /** 経路設計サービスの振る舞いをここで決める。実サービスは起こさない。 */
    private static final AtomicBoolean UNAVAILABLE = new AtomicBoolean();

    /**
     * 探索へ渡った条件。
     *
     * <p><b>スタブが引数を捨てると、コントローラが条件を落としても全部緑になる</b>
     * （IT6 レビュー 高）。投影から条件を読む配線は、ここでしか踏まれない。</p>
     */
    private static final java.util.concurrent.atomic.AtomicReference<
            com.example.cargotracker.booking.application.port.RouteSearchRequest> LAST_REQUEST =
            new java.util.concurrent.atomic.AtomicReference<>();

    @TestConfiguration
    static class StubFinder {

        @Bean
        @Primary
        RouteCandidateFinder stubRouteCandidateFinder() {
            return request -> {
                LAST_REQUEST.set(request);
                if (UNAVAILABLE.get()) {
                    throw new RouteCandidateFinder.RouteSearchUnavailable(
                            "経路設計サービスに問い合わせられませんでした", null);
                }
                return new RouteCandidateFinder.RouteCandidates(
                        List.of(new RouteCandidate(
                                List.of(new Leg("V-IT-001", Location.of("JPTYO"),
                                        Location.of("USNYC"),
                                        Instant.parse("2026-09-10T09:00:00Z"),
                                        Instant.parse("2026-09-24T18:00:00Z"))),
                                14, true)),
                        true);
            };
        }
    }

    static class JsonMap extends LinkedHashMap<String, Object> {
        private static final long serialVersionUID = 1L;
    }

    @LocalServerPort
    private int port;

    private final RestClient rest = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    private String url(String path) {
        return "http://localhost:" + port + "/api/v1/booking/bookings" + path;
    }

    /** 予約を 1 件作り、投影に入るまで待つ。 */
    private String bookedId() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("shipperId", "SHP-RC-" + System.nanoTime());
        body.put("originUnLocode", "JPTYO");
        body.put("destinationUnLocode", "USNYC");
        body.put("arrivalDeadline", "2026-12-01");
        body.put("cargoType", "GENERAL");
        body.put("weightKg", "1200");
        body.put("lengthCm", "120");
        body.put("widthCm", "80");
        body.put("heightCm", "100");
        body.put("quantity", 10);
        body.put("productName", "経路候補の確認");

        ResponseEntity<JsonMap> created = rest.post().uri(url(""))
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().toEntity(JsonMap.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String bookingId = String.valueOf(created.getBody().get("bookingId"));

        await("予約の投影が入る").atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> rest.get().uri(url("/" + bookingId))
                        .retrieve().toEntity(JsonMap.class)
                        .getStatusCode() == HttpStatus.OK);
        return bookingId;
    }

    @Test
    @DisplayName("経路設計サービスに問い合わせられないときは 503（空の候補一覧にしない）")
    @SuppressWarnings("unchecked")
    void returnsServiceUnavailable() {
        UNAVAILABLE.set(true);
        try {
            ResponseEntity<JsonMap> response = rest.get().uri(url("/" + bookedId()
                    + "/route-candidates")).retrieve().toEntity(JsonMap.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody().get("code")).isEqualTo("ROUTE_SEARCH_UNAVAILABLE");
            // 空の候補一覧を返していないこと。返すと「候補が無い」と読まれる。
            assertThat(response.getBody()).doesNotContainKey("candidates");
        } finally {
            UNAVAILABLE.set(false);
        }
    }

    @Test
    @DisplayName("応答の形が画面の前提と一致する（候補・区間・所要日数・直行・打ち切り）")
    @SuppressWarnings("unchecked")
    void returnsTheShapeTheScreenExpects() {
        ResponseEntity<JsonMap> response = rest.get()
                .uri(url("/" + bookedId() + "/route-candidates"))
                .retrieve().toEntity(JsonMap.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("truncated")).isEqualTo(true);

        List<Map<String, Object>> candidates =
                (List<Map<String, Object>>) response.getBody().get("candidates");
        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.get("transitDays")).isEqualTo(14);
            assertThat(candidate.get("direct")).isEqualTo(true);
            // **費用の欄は無い。** 料金表は US21 が正典で、0 を返すと
            // 「費用 0 円の経路」と読める。
            assertThat(candidate).doesNotContainKey("cost");

            List<Map<String, Object>> legs = (List<Map<String, Object>>) candidate.get("legs");
            assertThat(legs).singleElement().satisfies(leg -> {
                assertThat(leg.get("voyageNumber")).isEqualTo("V-IT-001");
                assertThat(leg.get("loadUnLocode")).isEqualTo("JPTYO");
                assertThat(leg.get("unloadUnLocode")).isEqualTo("USNYC");
                assertThat(leg).containsKeys("loadTime", "unloadTime");
            });
        });
    }

    @Test
    @DisplayName("US10: 調整した条件が探索へ渡る（デモ項目 2 の配線）")
    void adjustedConditionsReachTheSearch() {
        // **この検査だけが、投影から条件を読む配線を踏む。** 集約・投影・ACL・探索器の
        // 検査はそれぞれ自分の層しか見ておらず、コントローラの組み立てを
        // `List.of(), null` に潰しても全部緑になる（IT6 レビュー 高）。
        String bookingId = bookedId();
        // 条件を調整できるのは経路設計へ引き渡したあとだけ。
        assertThat(rest.post().uri(url("/" + bookingId + "/routing-request"))
                .header("X-Auth-Username", "sales01")
                .retrieve().toEntity(JsonMap.class).getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(rest.get().uri(url("/" + bookingId)).retrieve()
                        .toEntity(JsonMap.class).getBody().get("routingStatus"))
                        .isEqualTo("ROUTING_REQUESTED"));

        assertThat(rest.put()
                .uri(url("/" + bookingId + "/route-specification"))
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Auth-Username", "routing01")
                .body(java.util.Map.of("arrivalDeadline", "2027-01-31",
                        "excludeUnLocodes", List.of("SGSIN", "HKHKG"),
                        "departFromUnLocode", "JPOSA"))
                .retrieve().toEntity(JsonMap.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            LAST_REQUEST.set(null);
            rest.get().uri(url("/" + bookingId + "/route-candidates"))
                    .retrieve().toEntity(JsonMap.class);
            assertThat(LAST_REQUEST.get()).isNotNull();
            assertThat(LAST_REQUEST.get().excludePorts())
                    .extracting(excluded -> excluded.unLocode().value())
                    .containsExactlyInAnyOrder("SGSIN", "HKHKG");
            assertThat(LAST_REQUEST.get().departFrom()).isNotNull();
            assertThat(LAST_REQUEST.get().departFrom().unLocode().value()).isEqualTo("JPOSA");
            assertThat(LAST_REQUEST.get().arrivalDeadline())
                    .isEqualTo(java.time.LocalDate.of(2027, 1, 31));
        });
    }
}
