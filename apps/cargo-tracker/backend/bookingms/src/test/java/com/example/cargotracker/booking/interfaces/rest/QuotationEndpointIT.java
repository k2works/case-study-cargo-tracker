package com.example.cargotracker.booking.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.booking.application.port.RouteCandidateFinder;
import com.example.cargotracker.booking.application.port.RouteSearchRequest;
import com.example.cargotracker.booking.domain.model.valueobjects.Leg;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteCandidate;
import com.example.cargotracker.shared.domain.location.Location;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
 * 輸送見積の REST を HTTP 越しに固定する（US01 / S12・S13）。
 *
 * <p><b>層ごとの検査は自分の層しか見ない。</b> 集約・投影・ACL がそれぞれ緑でも、
 * つなぐ配線が抜けていれば見積は作れない。ここは受付から読み出しまでを 1 本で踏む。</p>
 *
 * <p><b>スタブは引数を捨てない。</b> 捨てると、コントローラが条件を落としても
 * 全部緑になる（IT6 レビュー 高で実際に起きた）。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class QuotationEndpointIT extends AbstractAxonIntegrationTest {

    /** 経路設計サービスの振る舞いをここで決める。実サービスは起こさない。 */
    private static final AtomicBoolean UNAVAILABLE = new AtomicBoolean();

    /** 候補を返すか。<b>0 件と「数えられなかった」は別物</b>なので分けて踏む。 */
    private static final AtomicBoolean NO_CANDIDATE = new AtomicBoolean();

    /** 探索へ渡った条件。<b>捨てると配線の抜けが素通りする。</b> */
    private static final AtomicReference<RouteSearchRequest> LAST_REQUEST =
            new AtomicReference<>();

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
                if (NO_CANDIDATE.get()) {
                    return new RouteCandidateFinder.RouteCandidates(List.of(), false);
                }
                return new RouteCandidateFinder.RouteCandidates(
                        List.of(new RouteCandidate(
                                List.of(new Leg("V-Q-001", Location.of("JPTYO"),
                                                Location.of("SGSIN"),
                                                Instant.parse("2026-10-01T09:00:00Z"),
                                                Instant.parse("2026-10-08T18:00:00Z")),
                                        new Leg("V-Q-002", Location.of("SGSIN"),
                                                Location.of("USNYC"),
                                                Instant.parse("2026-10-09T09:00:00Z"),
                                                Instant.parse("2026-10-20T18:00:00Z"))),
                                20, false)),
                        false);
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
        return "http://localhost:" + port + "/api/v1/booking/quotations" + path;
    }

    private ResponseEntity<JsonMap> create(Map<String, Object> body) {
        return rest.post().uri(url(""))
                .header("X-Auth-Username", "sales01")
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().toEntity(JsonMap.class);
    }

    private static Map<String, Object> request(Map<String, Object> overrides) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("originUnLocode", "JPTYO");
        body.put("destinationUnLocode", "USNYC");
        body.put("arrivalDeadline", LocalDate.now().plusDays(60).toString());
        body.put("cargoType", "GENERAL");
        body.put("weightKg", "1200");
        body.putAll(overrides);
        return body;
    }

    /** 見積が読めるまで待つ。投影は非同期に追いつく。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> awaitQuotation(String quotationId) {
        await("見積の投影が入る").atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> rest.get().uri(url("/" + quotationId))
                        .retrieve().toEntity(JsonMap.class).getStatusCode() == HttpStatus.OK);
        return rest.get().uri(url("/" + quotationId))
                .retrieve().toEntity(JsonMap.class).getBody();
    }

    @Test
    @DisplayName("US01 §1〜4: 要件から候補と概算が出て、見積番号が発行される")
    @SuppressWarnings("unchecked")
    void createsAQuotationWithCandidates() {
        UNAVAILABLE.set(false);
        NO_CANDIDATE.set(false);

        ResponseEntity<JsonMap> created = create(request(Map.of()));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        String quotationId = String.valueOf(created.getBody().get("quotationId"));
        assertThat(quotationId)
                .as("列は VARCHAR(36)。あふれると投影だけが静かに退避される")
                .hasSizeLessThanOrEqualTo(36);

        // **探索へ条件が渡っている。** 落とすと、まったく別の経路の見積になる。
        assertThat(LAST_REQUEST.get().origin().unLocode().value()).isEqualTo("JPTYO");
        assertThat(LAST_REQUEST.get().destination().unLocode().value()).isEqualTo("USNYC");

        Map<String, Object> view = awaitQuotation(quotationId);
        assertThat(view.get("originUnLocode")).isEqualTo("JPTYO");
        assertThat(new java.math.BigDecimal(String.valueOf(view.get("estimatedAmount"))))
                // 50,000 × (2.5 + 6.0) × 1.2 × 1.0 = 510,000
                .as("請求と同じ料率・同じ式（RateTableParityTest が同一性を固定）")
                .isEqualByComparingTo("510000");
        assertThat(view.get("hasDeadlineMeetingCandidate")).isEqualTo(true);

        List<Map<String, Object>> candidates =
                (List<Map<String, Object>>) view.get("candidates");
        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.get("voyageNumbers"))
                    .as("航海番号が読めなければ、営業担当者は荷主に案を説明できない")
                    .isEqualTo("V-Q-001 > V-Q-002");
            assertThat(candidate.get("transitDays")).isEqualTo(20);
            assertThat(new java.math.BigDecimal(
                    String.valueOf(candidate.get("estimatedCost"))))
                    .isEqualByComparingTo("510000");
        });
    }

    @Test
    @DisplayName("US01 §5: 期限に間に合う候補が無くても見積は作れる（0 件と『数えられない』は別）")
    void createsAQuotationEvenWithoutCandidates() {
        UNAVAILABLE.set(false);
        NO_CANDIDATE.set(true);

        ResponseEntity<JsonMap> created = create(request(Map.of()));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> view =
                awaitQuotation(String.valueOf(created.getBody().get("quotationId")));

        assertThat(view.get("hasDeadlineMeetingCandidate"))
                .as("「間に合う経路がありません」も荷主に返すべき答え")
                .isEqualTo(false);
        assertThat((List<?>) view.get("candidates")).isEmpty();
        assertThat(new java.math.BigDecimal(String.valueOf(view.get("estimatedAmount"))))
                .as("候補が無ければ 0 円（見積そのものは成り立つ）")
                .isEqualByComparingTo("0");

        NO_CANDIDATE.set(false);
    }

    @Test
    @DisplayName("探索が落ちているときは断る（0 件の見積として保存しない）")
    void refusesWhenRouteSearchIsUnavailable() {
        UNAVAILABLE.set(true);
        try {
            assertThat(create(request(Map.of())).getStatusCode())
                    .as("0 件で保存すると「間に合う経路が無い」と読まれ、誤った判断に使われる")
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        } finally {
            UNAVAILABLE.set(false);
        }
    }

    @Test
    @DisplayName("不変条件 1: 出発地と目的地が同じ見積は作れない")
    void refusesTheSameOriginAndDestination() {
        UNAVAILABLE.set(false);
        assertThat(create(request(Map.of("destinationUnLocode", "JPTYO"))).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    @DisplayName("危険物は危険物申告が要る（予約で初めて断られて出し直しにしない）")
    void requiresTheHazardousDeclaration() {
        UNAVAILABLE.set(false);
        assertThat(create(request(Map.of("cargoType", "HAZARDOUS"))).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);

        assertThat(create(request(Map.of("cargoType", "HAZARDOUS",
                "hazardousImoClass", "3", "hazardousUnNumber", "UN1263"))).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    /** 予約を 1 件作る。**見積番号を渡せる**（渡さない予約もある）。 */
    private ResponseEntity<JsonMap> book(String quotationId, Map<String, Object> overrides) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("shipperId", "SHP-Q-" + System.nanoTime());
        body.put("originUnLocode", "JPTYO");
        body.put("destinationUnLocode", "USNYC");
        body.put("arrivalDeadline", LocalDate.now().plusDays(60).toString());
        body.put("cargoType", "GENERAL");
        body.put("weightKg", "1200");
        body.put("lengthCm", "120");
        body.put("widthCm", "80");
        body.put("heightCm", "100");
        body.put("quantity", 10);
        body.put("productName", "見積からの予約");
        if (quotationId != null) {
            body.put("quotationId", quotationId);
        }
        body.putAll(overrides);
        return rest.post().uri("http://localhost:" + port + "/api/v1/booking/bookings")
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().toEntity(JsonMap.class);
    }

    @Test
    @DisplayName("不変条件 3: 見積と違う項目は断らずに項目名で知らせる")
    @SuppressWarnings("unchecked")
    void tellsWhichTermsDifferFromTheQuotation() {
        UNAVAILABLE.set(false);
        NO_CANDIDATE.set(false);
        String quotationId = String.valueOf(create(request(Map.of()))
                .getBody().get("quotationId"));
        awaitQuotation(quotationId);

        // 重量だけ変えて予約する。**断らない**——荷主の事情は見積のあとで変わる。
        ResponseEntity<JsonMap> booked = book(quotationId, Map.of("weightKg", "1500"));

        assertThat(booked.getStatusCode())
                .as("違いがあっても予約は通す（断ると業務が止まる）")
                .isEqualTo(HttpStatus.CREATED);
        List<String> differences =
                (List<String>) booked.getBody().get("quotationDifferences");
        assertThat(differences)
                .as("項目名だけでは、営業担当者は見積を開き直して見比べることになる")
                .anySatisfy(difference -> assertThat(difference)
                        .contains("重量").contains("1200").contains("1500"));
    }

    @Test
    @DisplayName("見積どおりの予約では、異なる項目は出ない（毎回出ると読まれなくなる）")
    @SuppressWarnings("unchecked")
    void reportsNoDifferenceWhenTheBookingMatchesTheQuotation() {
        UNAVAILABLE.set(false);
        NO_CANDIDATE.set(false);
        String quotationId = String.valueOf(create(request(Map.of()))
                .getBody().get("quotationId"));
        Map<String, Object> quoted = awaitQuotation(quotationId);

        ResponseEntity<JsonMap> booked = book(quotationId,
                Map.of("arrivalDeadline", String.valueOf(quoted.get("arrivalDeadline"))));

        assertThat((List<String>) booked.getBody().get("quotationDifferences"))
                .as("1200 と 1200.00 を「違う」と出すと、見積どおりの予約が毎回違うと言われる")
                .isEmpty();
    }

    @Test
    @DisplayName("見積を経ない予約でも通る（見積番号は任意）")
    @SuppressWarnings("unchecked")
    void booksWithoutAQuotation() {
        ResponseEntity<JsonMap> booked = book(null, Map.of());

        assertThat(booked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat((List<String>) booked.getBody().get("quotationDifferences")).isEmpty();
    }

    @Test
    @DisplayName("知らない見積番号でも予約は通す（打ち間違いで業務を止めない）")
    @SuppressWarnings("unchecked")
    void booksEvenWhenTheQuotationIsUnknown() {
        ResponseEntity<JsonMap> booked = book("Q-unknown", Map.of());

        assertThat(booked.getStatusCode())
                .as("違いを知らせられないだけで、予約そのものは通す")
                .isEqualTo(HttpStatus.CREATED);
        assertThat((List<String>) booked.getBody().get("quotationDifferences")).isEmpty();
    }

    @Test
    @DisplayName("投影が追いつく前は 202（「作ったのに読めない」を「ありません」に化けさせない）")
    void answersPendingBeforeTheProjectionCatchesUp() {
        assertThat(rest.get().uri(url("/Q-unknown")).retrieve().toEntity(JsonMap.class)
                .getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);
    }
}
