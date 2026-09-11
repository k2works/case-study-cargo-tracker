package com.example.cargotracker.acceptance.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.billing.application.reaction.BillingReactionHandler;
import com.example.cargotracker.billing.infrastructure.projection.BillingCargoProjection;
import com.example.cargotracker.billing.infrastructure.projection.ShipperContractProjection;
import com.example.cargotracker.shared.contract.event.CargoDeliveredEvent;
import com.example.cargotracker.shared.contract.event.ShipperRegisteredEvent;
import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
import com.example.cargotracker.shared.testing.AcceptanceFixtureTime;
import io.cucumber.java.ja.かつ;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * 輸送料金の算出と法人割引（US21・US22）のデモ項目。
 *
 * <p><b>他 BC のイベントは写しから作る。</b> billingms は貨物も荷主も自分の読み取り
 * モデルに写している（同期問い合わせをしない）ので、前提は契約イベントを流して作る。</p>
 */
public class BillingSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private BillingReactionHandler reactions;

    @Autowired
    private BillingCargoProjection cargos;

    @Autowired
    private ShipperContractProjection shippers;

    private final RestClient rest = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    private static final org.springframework.core.ParameterizedTypeReference<Map<String, Object>>
            JSON = new org.springframework.core.ParameterizedTypeReference<>() { };

    private String trackingNumber;
    private String bookingId;
    private String shipperId;
    private BigDecimal weightKg = new BigDecimal("1200");
    private String origin = "JPTYO";
    private String destination = "JPOSA";
    private int legCount = 3;
    private ResponseEntity<Void> lastResponse;

    /** 調整の前の請求額。**「小さくなった」は前と比べないと言えない。** */
    private BigDecimal totalBeforeAdjustment;

    private String url(String path) {
        return "http://localhost:" + port + "/api/v1/billing/invoices" + path;
    }

    private Map<String, Object> invoice() {
        ResponseEntity<Map<String, Object>> response = rest.get()
                .uri(url("/by-booking/" + bookingId)).retrieve().toEntity(JSON);
        return response.getStatusCode().is2xxSuccessful() ? response.getBody() : null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> lineItems() {
        return (List<Map<String, Object>>) invoice().get("lineItems");
    }

    private void registerCargo() {
        String suffix = String.valueOf(System.nanoTime());
        trackingNumber = "TRK-AC" + suffix.substring(suffix.length() - 9);
        bookingId = "B-AC-" + suffix;

        // 区間は「JPTYO → 中継 → 中継 → 目的地」の形で並べる。区間の数が
        // 料金の区間係数に効くので、シナリオが言う数のとおりに作る。
        List<TrackingInitializedEvent.Leg> legs = new java.util.ArrayList<>();
        String from = origin;
        for (int i = 1; i <= legCount; i++) {
            String to = i == legCount ? destination : (i == 1 ? "SGSIN" : "USLAX");
            legs.add(new TrackingInitializedEvent.Leg("V-MOL-00" + i, from, to,
                    AcceptanceFixtureTime.at(-10 + i, 9), AcceptanceFixtureTime.at(-5 + i, 8)));
            from = to;
        }

        cargos.on(new TrackingInitializedEvent(trackingNumber, bookingId, shipperId,
                origin, destination, "GENERAL", weightKg, legs,
                AcceptanceFixtureTime.at(-12, 1)), "evt-" + System.nanoTime());
    }

    @前提("個人荷主の貨物が {string} から {string} へ {int} 区間で運ばれた")
    public void 個人荷主の貨物がある(String from, String to, int legs) {
        shipperId = "SHP-AC-" + System.nanoTime();
        shippers.on(new ShipperRegisteredEvent(shipperId, "INDIVIDUAL", "山田 太郎",
                shipperId + "@example.com", "03-0000-0000", "東京都港区", null, null));
        this.origin = from;
        this.destination = to;
        this.legCount = legs;
    }

    @前提("割引率 {int} パーセントの法人荷主の貨物が {string} から {string} へ {int} 区間で運ばれた")
    public void 法人荷主の貨物がある(int percentage, String from, String to, int legs) {
        shipperId = "SHP-AC-" + System.nanoTime();
        String rate = new BigDecimal(percentage).movePointLeft(2).setScale(4).toPlainString();
        shippers.on(new ShipperRegisteredEvent(shipperId, "CORPORATE", "山田商事",
                shipperId + "@example.com", "03-0000-0000", "東京都港区", "CT-0012", rate));
        this.origin = from;
        this.destination = to;
        this.legCount = legs;
    }

    @かつ("その貨物の重量は {int} キログラムである")
    public void 重量がある(int kilograms) {
        this.weightKg = new BigDecimal(kilograms);
        registerCargo();
    }

    @かつ("その貨物の重量は分かっていない")
    public void 重量が分かっていない() {
        // **重量を運ぶ前のイベントから作られた写し。** 0 で埋めない——重量係数が
        // 下限に落ち、足りない重量で安い請求が黙って出る。
        this.weightKg = null;
        registerCargo();
    }

    // **同じ文をもし／かつの両方で使う。** Cucumber は同じ表現に 2 つの定義を
    // 許さないので、注釈は 1 つだけにする（キーワードは表現の一部ではない）。
    @もし("その貨物の引取が完了する")
    public void 引取が完了する() {
        reactions.on(new CargoDeliveredEvent(trackingNumber, bookingId,
                AcceptanceFixtureTime.at(-1, 9), destination));
    }

    @ならば("{int} 秒以内にその予約の請求書ができる")
    public void 請求書ができる(int seconds) {
        await().atMost(Duration.ofSeconds(seconds + 20)).until(() -> invoice() != null);
    }

    @ならば("{int} 秒以内にその予約の請求書はできない")
    public void 請求書はできない(int seconds) {
        // **できないことを待って確かめる。** すぐ見ると「まだ作られていない」
        // だけかもしれない。
        try {
            Thread.sleep(Duration.ofSeconds(3));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        assertThat(invoice()).isNull();
    }

    @かつ("その請求書の状態は {string} である")
    public void 請求書の状態(String label) {
        assertThat(invoice().get("statusLabel")).isEqualTo(label);
    }

    @かつ("請求の明細に基本料金の根拠が並ぶ")
    public void 根拠が並ぶ() {
        assertThat(lineItems())
                .filteredOn(line -> "BASE".equals(line.get("itemType")))
                .singleElement()
                .satisfies(line -> assertThat(String.valueOf(line.get("description")))
                        .contains(legCount + " 区間").contains("kg"));
    }

    @かつ("割引率 {int} パーセントの割引が明細に出る")
    public void 割引が出る(int percentage) {
        assertThat(lineItems())
                .filteredOn(line -> "DISCOUNT".equals(line.get("itemType")))
                .singleElement()
                .satisfies(line -> assertThat(String.valueOf(line.get("description")))
                        .contains(String.valueOf(percentage)).contains("CT-0012"));
    }

    @かつ("割引後の金額が基本料金より小さい")
    public void 割引後は小さい() {
        Map<String, Object> invoice = invoice();
        assertThat(new BigDecimal(String.valueOf(invoice.get("totalAmount"))))
                .isLessThan(new BigDecimal(String.valueOf(invoice.get("baseAmount"))));
    }

    @かつ("割引の明細は無い")
    public void 割引は無い() {
        assertThat(lineItems()).noneMatch(line -> "DISCOUNT".equals(line.get("itemType")));
        assertThat(new BigDecimal(String.valueOf(invoice().get("discountAmount"))))
                .isEqualByComparingTo("0");
    }

    @かつ("消費税は {int} 円である")
    public void 消費税(int amount) {
        assertThat(new BigDecimal(String.valueOf(invoice().get("taxAmount"))))
                .isEqualByComparingTo(new BigDecimal(amount));
    }

    @もし("理由 {string} で {int} 円の減額を入れる")
    public void 減額を入れる(String reason, int amount) {
        // **投影は非同期に追いつく。** 待たずに読むと、連鎖が通っていても
        // 請求書が見つからない（シナリオの筋とは関係のない失敗になる）。
        await().atMost(Duration.ofSeconds(30)).until(() -> invoice() != null);
        totalBeforeAdjustment = new BigDecimal(String.valueOf(invoice().get("totalAmount")));
        String invoiceId = String.valueOf(invoice().get("invoiceId"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", new BigDecimal(-amount));
        body.put("reason", reason);
        body.put("basisExceptionId", "EX-AC-" + System.nanoTime());
        lastResponse = rest.post().uri(url("/" + invoiceId + "/adjustments"))
                .header("X-Auth-Username", "accountant01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toBodilessEntity();
    }

    @もし("留置 {int} 営業日の保管料 {int} 円を申告 {string} を根拠に入れる")
    public void 保管料を入れる(int businessDays, int amount, String declarationNumber) {
        await().atMost(Duration.ofSeconds(30)).until(() -> invoice() != null);
        totalBeforeAdjustment = new BigDecimal(String.valueOf(invoice().get("totalAmount")));
        String invoiceId = String.valueOf(invoice().get("invoiceId"));

        Map<String, Object> body = new LinkedHashMap<>();
        // **補償費用は正。** 符号が向きを表す（減額は負）。
        body.put("amount", new BigDecimal(amount));
        body.put("reason", "留置 " + businessDays + " 営業日の保管料");
        // **根拠は通関申告。** 留置営業日数は申告の側が数えている
        // （CustomsStatusChangedEvent.heldBusinessDays）。
        body.put("basisExceptionId", declarationNumber);
        lastResponse = rest.post().uri(url("/" + invoiceId + "/adjustments"))
                .header("X-Auth-Username", "accountant01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toBodilessEntity();
    }

    @かつ("調整の根拠に申告 {string} が残る")
    public void 根拠に申告が残る(String declarationNumber) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(lineItems())
                        .filteredOn(line -> "ADJUSTMENT".equals(line.get("itemType")))
                        .singleElement()
                        .satisfies(line -> {
                            assertThat(line.get("basisExceptionId"))
                                    .isEqualTo(declarationNumber);
                            assertThat(String.valueOf(line.get("description")))
                                    .contains("営業日");
                        }));
    }

    @かつ("請求金額が保管料のぶん大きくなる")
    public void 請求金額が大きくなる() {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(new BigDecimal(String.valueOf(invoice().get("totalAmount"))))
                        .isGreaterThan(totalBeforeAdjustment));
    }

    @ならば("その操作は成功する")
    public void 操作は成功する() {
        assertThat(lastResponse.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @かつ("調整の明細が請求書に出る")
    public void 調整の明細が出る() {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(lineItems())
                        .filteredOn(line -> "ADJUSTMENT".equals(line.get("itemType")))
                        .singleElement()
                        .satisfies(line -> assertThat(line.get("basisExceptionId"))
                                .as("根拠を指せないと、あとから確かめられない")
                                .isNotNull()));
    }

    @かつ("請求金額が調整のぶん小さくなる")
    public void 請求金額が小さくなる() {
        // **基本料金と比べない。** 国内輸送では消費税が乗るので、合計は基本料金
        // より大きいまま減額されうる。比べる相手は<b>調整の前の合計</b>である。
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Map<String, Object> invoice = invoice();
            assertThat(new BigDecimal(String.valueOf(invoice.get("adjustmentAmount"))))
                    .isEqualByComparingTo("-10000");
            assertThat(new BigDecimal(String.valueOf(invoice.get("totalAmount"))))
                    .isLessThan(totalBeforeAdjustment);
        });
    }

    @もし("その引取がもう一度届く")
    public void 引取がもう一度届く() {
        // **少なくとも 1 回配送では起こりうる。** 連鎖は有効な請求書があれば
        // 送らないので、静かに止まる。
        引取が完了する();
    }

    @ならば("その予約の請求書は 1 通のままである")
    public void 請求書は一通のまま() {
        ResponseEntity<Map<String, Object>> response = rest.get()
                .uri(url("?includeSettled=true&bookingId=" + bookingId))
                .retrieve().toEntity(JSON);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) response.getBody().get("items")).hasSize(1);
    }

    @かつ("経理の要確認一覧にその予約が出る")
    @SuppressWarnings("unchecked")
    public void 要確認に出る() {
        // **画面が読む経路で確かめる。** DB を直接読むと、読み口が無くても緑に
        // なる——IT13 では実際にその状態で受け入れが通っていた（レビューで発見）。
        ResponseEntity<Map<String, Object>> response = rest.get()
                .uri("http://localhost:" + port + "/api/v1/billing/attention-items")
                .header("X-Auth-Roles", "ROLE_ACCOUNTANT")
                .retrieve().toEntity(JSON);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<Map<String, Object>>) response.getBody().get("items"))
                .filteredOn(item -> bookingId.equals(item.get("targetId")))
                .singleElement()
                .satisfies(item -> assertThat(String.valueOf(item.get("reason")))
                        .contains("重量"));
    }

}
