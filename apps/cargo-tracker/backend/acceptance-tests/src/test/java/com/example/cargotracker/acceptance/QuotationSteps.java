package com.example.cargotracker.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ja.かつ;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * 輸送見積の作成（US01）のデモ項目。
 *
 * <p><b>経路探索は代役に差し替える。</b> この文脈で立ち上がるのは bookingms だけで、
 * routingms は居ない（同一 JVM に 2 つ載せると設定とマイグレーションが衝突する）。
 * 代役は {@link StubRouteCandidateFinder} が返す——<b>区間の数と航海番号は
 * シナリオが言うとおりに作る</b>ので、概算料金は区間の数で変わる。</p>
 *
 * <p><b>予約への写しまで踏む</b>（D5）。見積を作って終わりにすると、
 * 「見積番号で予約したら 5 項目が写る」という US01 の中核が未検査のまま残る。</p>
 */
public class QuotationSteps {

    /**
     * 3 区間・1,200kg・一般貨物の概算（JPTYO → SGSIN → USLAX → USNYC）。
     *
     * <p>50,000 ×（近海 2.5 + 遠洋 6.0 + 遠洋 6.0）× 1.2 = 870,000。
     * <b>請求の基本料金と同じ料率から出る</b>——料率が 1 つの出典であることは
     * {@code RateTableParityTest} が実ファイルを読んで固定する（ADR-0016）。
     * ここが赤くなったら、料率か式のどちらかが片方だけ動いている。</p>
     */
    private static final BigDecimal EXPECTED_ESTIMATE = new BigDecimal("870000");

    @LocalServerPort
    private int port;

    @Autowired
    private StubRouteCandidateFinder routes;

    @Autowired
    private ShipperRegistrationSteps shippers;

    /** 成功／失敗の判定は {@link ConditionAndNotificationSteps} が持つ（1 か所）。 */
    @Autowired
    private ConditionAndNotificationSteps operations;

    private final RestClient rest = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    private static final org.springframework.core.ParameterizedTypeReference<Map<String, Object>>
            JSON = new org.springframework.core.ParameterizedTypeReference<>() { };

    private String quotationId;
    private Map<String, Object> quotation;
    private ResponseEntity<Map<String, Object>> lastResponse;
    private List<String> differences;

    /** 判定を預ける側の型に合わせる。**判定そのものは 1 か所のまま**。 */
    private static ResponseEntity<BookingRegistrationSteps.JsonMap> asJsonMap(
            ResponseEntity<Map<String, Object>> response) {
        BookingRegistrationSteps.JsonMap body = new BookingRegistrationSteps.JsonMap();
        if (response.getBody() != null) {
            body.putAll(response.getBody());
        }
        return ResponseEntity.status(response.getStatusCode()).body(body);
    }

    private String url(String path) {
        return "http://localhost:" + port + "/api/v1/booking/quotations" + path;
    }

    @前提("{string} から {string} へ {int} 区間で行ける航海がある")
    public void 行ける航海がある(String from, String to, int legs) {
        routes.reachable(from, to, legs);
    }

    @もし("営業担当者が {string} から {string} へ {int} キログラムの一般貨物の見積を作る")
    public void 見積を作る(String from, String to, int kilograms) {
        create(from, to, LocalDate.now().plusMonths(3), new BigDecimal(kilograms));
    }

    @もし("営業担当者が到着期限を明日にして {string} から {string} への見積を作る")
    public void 期限を明日にして見積を作る(String from, String to) {
        // **明日には着かない。** 候補はあるが期限に間に合わない——0 件とは別の状態。
        create(from, to, LocalDate.now().plusDays(1), new BigDecimal("1200"));
    }

    private void create(String from, String to, LocalDate deadline, BigDecimal kilograms) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("originUnLocode", from);
        body.put("destinationUnLocode", to);
        body.put("arrivalDeadline", deadline.toString());
        body.put("cargoType", "GENERAL");
        body.put("weightKg", kilograms);
        lastResponse = rest.post().uri(url(""))
                .header("X-Auth-Username", "sales01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toEntity(JSON);
        operations.record(asJsonMap(lastResponse));
        quotationId = lastResponse.getStatusCode().is2xxSuccessful()
                ? String.valueOf(lastResponse.getBody().get("quotationId")) : null;
    }

    @かつ("見積番号が発行される")
    public void 見積番号が発行される() {
        assertThat(quotationId).isNotBlank();
    }

    @ならば("{int} 秒以内にその見積が読める")
    public void 見積が読める(int seconds) {
        SharedSteps.awaitWithin(seconds + 20, () -> {
            ResponseEntity<Map<String, Object>> response = rest.get()
                    .uri(url("/" + quotationId)).retrieve().toEntity(JSON);
            if (!response.getStatusCode().is2xxSuccessful()
                    || response.getBody().get("candidates") == null) {
                return false;
            }
            quotation = response.getBody();
            return true;
        }, "見積が読めるようになる");
    }

    @かつ("見積の候補ごとに経由港・所要日数・概算料金・航海番号が出る")
    @SuppressWarnings("unchecked")
    public void 候補ごとに四項目が出る() {
        List<Map<String, Object>> candidates =
                (List<Map<String, Object>>) quotation.get("candidates");
        assertThat(candidates).isNotEmpty();
        assertThat(candidates).allSatisfy(candidate -> {
            // **4 つとも要る。** どれか 1 つでも欠けると、営業担当者は
            // 荷主に案を説明できない。
            assertThat(String.valueOf(candidate.get("ports")))
                    .as("経由港は候補ごとに違う。出発地と目的地だけでは案を選び分けられない")
                    .contains("SGSIN");
            assertThat(candidate.get("transitDays")).isNotNull();
            assertThat(new BigDecimal(String.valueOf(candidate.get("estimatedCost"))))
                    .isGreaterThan(BigDecimal.ZERO);
            assertThat(String.valueOf(candidate.get("voyageNumbers"))).contains("V-");
        });
    }

    @かつ("概算料金は同じ条件の請求の基本料金と一致する")
    public void 概算は請求の基本料金と一致する() {
        assertThat(new BigDecimal(String.valueOf(quotation.get("estimatedAmount"))))
                .as("料率の出典は 1 つ（ADR-0016）。片方だけ動くとここが赤くなる")
                .isEqualByComparingTo(EXPECTED_ESTIMATE);
    }

    @かつ("期限に間に合う候補が無いことが分かる")
    public void 間に合う候補が無い() {
        assertThat((Boolean) quotation.get("hasDeadlineMeetingCandidate"))
                .as("「候補が 0 件」と「間に合う候補が無い」は別（断らずに知らせる）")
                .isFalse();
    }

    @もし("その見積で重量を {int} キログラムに変えて予約する")
    public void 重量を変えて予約する(int kilograms) {
        book(new BigDecimal(kilograms));
    }

    @もし("その見積のままで予約する")
    public void そのままで予約する() {
        book(new BigDecimal("1200"));
    }

    @SuppressWarnings("unchecked")
    private void book(BigDecimal kilograms) {
        String shipperId = shippers.registerShipper();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("shipperId", shipperId);
        body.put("quotationId", quotationId);
        body.put("originUnLocode", String.valueOf(quotation.get("originUnLocode")));
        body.put("destinationUnLocode", String.valueOf(quotation.get("destinationUnLocode")));
        body.put("arrivalDeadline", String.valueOf(quotation.get("arrivalDeadline")));
        body.put("cargoType", "GENERAL");
        body.put("weightKg", kilograms);
        body.put("lengthCm", new BigDecimal("120"));
        body.put("widthCm", new BigDecimal("80"));
        body.put("heightCm", new BigDecimal("100"));
        body.put("quantity", 10);
        body.put("productName", "自動車部品-" + System.nanoTime());

        lastResponse = rest.post().uri("http://localhost:" + port + "/api/v1/booking/bookings")
                .header("X-Auth-Username", "sales01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toEntity(JSON);
        operations.record(asJsonMap(lastResponse));
        differences = lastResponse.getStatusCode().is2xxSuccessful()
                ? (List<String>) lastResponse.getBody().get("quotationDifferences")
                : List.of();
    }

    @かつ("見積と異なる項目として {string} が知らされる")
    public void 異なる項目が知らされる(String term) {
        assertThat(differences)
                .as("断らずに知らせる（不変条件 3）。応答に載らなければ誰も気づけない")
                .anySatisfy(difference -> assertThat(difference).contains(term));
    }

    @かつ("見積と異なる項目は無い")
    public void 異なる項目は無い() {
        assertThat(differences)
                .as("毎回何か出ると読まれなくなる")
                .isEmpty();
    }
}
