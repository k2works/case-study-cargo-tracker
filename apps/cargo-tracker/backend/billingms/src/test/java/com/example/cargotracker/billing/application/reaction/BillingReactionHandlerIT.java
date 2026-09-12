package com.example.cargotracker.billing.application.reaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.billing.infrastructure.persistence.AttentionItemMapper;
import com.example.cargotracker.billing.infrastructure.persistence.BillingCargoSnapshotMapper;
import com.example.cargotracker.billing.infrastructure.projection.BillingCargoProjection;
import com.example.cargotracker.billing.infrastructure.projection.ShipperContractProjection;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.FindInvoiceOfBookingQuery;
import com.example.cargotracker.billing.infrastructure.query.InvoiceQueryHandler;
import com.example.cargotracker.shared.contract.event.CargoDeliveredEvent;
import com.example.cargotracker.shared.contract.event.CargoQuotedEvent;
import com.example.cargotracker.shared.contract.event.ShipperRegisteredEvent;
import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 引取 → 請求の連鎖（US21 §受入基準 1）と、その<b>補償経路</b>。
 *
 * <p><b>補償経路を 1 本ずつ検査する</b>（開発戦略の終盤の完了条件）。連鎖は
 * 「通る道」だけ確かめても、止まったときに何が起きるかを判別しない。ここでは
 * 材料が欠けた 3 つの場合——貨物の写しが無い・重量が無い・荷主の契約が無い——を
 * 別々に踏む。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BillingReactionHandlerIT extends AbstractAxonIntegrationTest {

    private static final Instant AT = Instant.parse("2026-09-28T01:00:00Z");

    @Autowired
    private BillingReactionHandler handler;

    @Autowired
    private BillingCargoProjection cargoProjection;

    @Autowired
    private ShipperContractProjection shipperProjection;

    @Autowired
    private InvoiceQueryHandler queries;

    @Autowired
    private AttentionItemMapper attentionItems;

    @Autowired
    private BillingCargoSnapshotMapper cargos;

    @Autowired
    private com.example.cargotracker.billing.infrastructure.projection.BookingQuotationProjection
            quotationProjection;

    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    private record Fixture(String trackingNumber, String bookingId, String shipperId) {
    }

    private Fixture cargo(BigDecimal weightKg, boolean withLegs, boolean withContract,
            String shipperType, String discountRate) {
        String suffix = String.valueOf(System.nanoTime());
        String trackingNumber = "TRK-RX" + suffix.substring(suffix.length() - 9);
        String bookingId = "B-RX-" + suffix;
        String shipperId = "SHP-RX-" + suffix;

        if (withContract) {
            shipperProjection.on(new ShipperRegisteredEvent(shipperId, shipperType, "山田商事",
                    shipperId + "@example.com", "03-0000-0000", "東京都港区",
                    discountRate == null ? null : "CT-0012", discountRate));
        }
        cargoProjection.on(new TrackingInitializedEvent(trackingNumber, bookingId, shipperId,
                "JPTYO", "USNYC", "GENERAL", weightKg,
                withLegs
                        ? List.of(new TrackingInitializedEvent.Leg("V-MOL-001", "JPTYO", "SGSIN",
                                        AT, AT.plusSeconds(86_400)),
                                new TrackingInitializedEvent.Leg("V-ONE-002", "SGSIN", "USNYC",
                                        AT.plusSeconds(90_000), AT.plusSeconds(600_000)))
                        : List.of(),
                AT), "evt-" + System.nanoTime());
        return new Fixture(trackingNumber, bookingId, shipperId);
    }

    private void deliver(Fixture fixture) {
        handler.on(new CargoDeliveredEvent(fixture.trackingNumber(), fixture.bookingId(),
                AT, "USNYC"));
    }

    /**
     * 請求書が読み取りモデルに現れるまで待つ。
     *
     * <p><b>投影は非同期に追いつく。</b> 待たずに読むと、連鎖が通っていても
     * null になる——そこで「動いていない」と読み違える。</p>
     */
    private com.example.cargotracker.billing.infrastructure.query.BillingQueries.InvoiceView
            awaitInvoice(String bookingId) {
        await().atMost(Duration.ofSeconds(30)).until(() ->
                queries.handle(new FindInvoiceOfBookingQuery(bookingId)) != null);
        return queries.handle(new FindInvoiceOfBookingQuery(bookingId));
    }

    private List<AttentionItemMapper.AttentionItemRow> attentionFor(String bookingId) {
        return attentionItems.findOpenByRole("ROLE_ACCOUNTANT").stream()
                .filter(item -> bookingId.equals(item.targetId()))
                .toList();
    }

    @Test
    @DisplayName("US21 §1: 引取済になった予約に請求書ができる（法人割引つき）")
    void createsTheInvoiceWhenTheCargoIsDelivered() {
        var fixture = cargo(new BigDecimal("1200"), true, true, "CORPORATE", "0.1500");

        deliver(fixture);

        var view = awaitInvoice(fixture.bookingId());
        assertThat(view).isNotNull();
        assertThat(view.baseAmount()).isEqualByComparingTo("510000");
        assertThat(view.discountAmount()).isEqualByComparingTo("76500");
        assertThat(view.taxAmount()).as("輸出は免税").isEqualByComparingTo("0");
        assertThat(view.totalAmount()).isEqualByComparingTo("433500");
    }

    @Test
    @DisplayName("注 N12: 見積から作った予約では、請求書に見積時の概算が載る")
    void carriesTheQuotedAmountFromTheBooking() {
        var fixture = cargo(new BigDecimal("1200"), true, true, "CORPORATE", "0.1500");
        // **予約の時点で届く。** 請求書を作るのは引取のあとなので、間に合う。
        quotationProjection.on(new CargoQuotedEvent(fixture.bookingId(),
                "Q-0123456789abcdef0123456789abcd", new BigDecimal("510000"), "JPY", AT));

        deliver(fixture);

        assertThat(awaitInvoice(fixture.bookingId()).quotedAmount())
                .as("載らないと S61 の差額行が永久に出ない（注 N12）")
                .isEqualByComparingTo("510000");
    }

    @Test
    @DisplayName("注 N12: 見積を経ない予約では概算は空のまま（0 円で埋めない）")
    void leavesTheQuotedAmountEmptyWithoutAQuotation() {
        var fixture = cargo(new BigDecimal("1200"), true, true, "CORPORATE", "0.1500");

        deliver(fixture);

        assertThat(awaitInvoice(fixture.bookingId()).quotedAmount())
                .as("0 で埋めると「0 円の見積があった」と読まれる")
                .isNull();
    }

    @Test
    @DisplayName("US22 §3: 個人荷主では割引が入らない")
    void doesNotDiscountIndividuals() {
        var fixture = cargo(new BigDecimal("1200"), true, true, "INDIVIDUAL", null);

        deliver(fixture);

        assertThat(awaitInvoice(fixture.bookingId()).discountAmount())
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("補償 1: 貨物の写しが来ていなければ再試行させる（捨てない）")
    void retriesWhenTheCargoSnapshotHasNotArrived() {
        // **投げ直すと Event Processor が再試行し、退避先が受け止める。**
        // 写しはあとから届くので、人に渡すより待つほうがよい。
        String trackingNumber = "TRK-MISSING-" + System.nanoTime();

        assertThatThrownBy(() -> handler.on(new CargoDeliveredEvent(trackingNumber,
                "B-MISSING-" + System.nanoTime(), AT, "USNYC")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("写し");
    }

    @Test
    @DisplayName("補償 2: 重量が分からなければ請求書を作らず、経理の要確認に出る")
    void recordsAttentionWhenTheWeightIsUnknown() {
        // **待っても入らない。** 重量を運ぶ前のイベントから作られた写しなので、
        // 再試行しても同じ。人に渡す。**足りない重量で安い請求を黙って出さない。**
        var fixture = cargo(null, true, true, "CORPORATE", "0.1500");

        deliver(fixture);

        assertThat(queries.handle(new FindInvoiceOfBookingQuery(fixture.bookingId()))).isNull();
        assertThat(attentionFor(fixture.bookingId()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.reason()).contains("重量");
                    assertThat(item.assignedRole()).isEqualTo("ROLE_ACCOUNTANT");
                });
    }

    @Test
    @DisplayName("補償 3: 荷主の契約が来ていなければ再試行させる")
    void retriesWhenTheShipperContractHasNotArrived() {
        var fixture = cargo(new BigDecimal("1200"), true, false, "CORPORATE", "0.1500");

        assertThatThrownBy(() -> deliver(fixture))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("契約");
    }

    @Test
    @DisplayName("補償 4: 区間が分からなければ請求書を作らず、経理の要確認に出る")
    void recordsAttentionWhenThereAreNoLegs() {
        var fixture = cargo(new BigDecimal("1200"), false, true, "CORPORATE", "0.1500");

        deliver(fixture);

        assertThat(queries.handle(new FindInvoiceOfBookingQuery(fixture.bookingId()))).isNull();
        assertThat(attentionFor(fixture.bookingId()))
                .singleElement()
                .satisfies(item -> assertThat(item.reason()).contains("区間"));
    }

    @Test
    @DisplayName("同じ引取が 2 度届いても請求書は 1 通（少なくとも 1 回配送）")
    void isIdempotentForRedeliveredEvents() {
        var fixture = cargo(new BigDecimal("1200"), true, true, "CORPORATE", "0.1500");

        deliver(fixture);
        awaitInvoice(fixture.bookingId());
        deliver(fixture);

        assertThat(queries.handle(new com.example.cargotracker.billing.infrastructure.query
                .BillingQueries.FindInvoicesQuery(true, fixture.bookingId())).items())
                .hasSize(1);
        assertThat(attentionFor(fixture.bookingId()))
                .as("2 度目は静かに止める（弾かれた事実を要確認に出さない）")
                .isEmpty();
    }

    @Test
    @DisplayName("貨物の写しは請求に要る材料をすべて持っている（読める形で確かめる）")
    void theCargoSnapshotCarriesWhatBillingNeeds() {
        var fixture = cargo(new BigDecimal("1200"), true, true, "CORPORATE", "0.1500");

        var row = cargos.find(fixture.trackingNumber());
        assertThat(row.weightKg()).isEqualByComparingTo("1200");
        assertThat(cargos.findLegs(fixture.trackingNumber())).hasSize(2);
    }

    @Test
    @DisplayName("請求書番号は人が読める形で、列に収まる（経理は番号で会話する）")
    void invoiceIdIsReadableAndFitsTheColumn() {
        // **素の UUID では画面でも問い合わせでも扱えない。** IT13 では逆に
        // "INV-" + UUID（40 文字）にして `VARCHAR(36)` に入らず、集約は受け付ける
        // のに投影だけが退避された——**退避先を見るまで気づけなかった**。
        var fixture = cargo(new BigDecimal("1200"), true, true, "INDIVIDUAL", null);

        deliver(fixture);

        String invoiceId = awaitInvoice(fixture.bookingId()).invoiceId();
        assertThat(invoiceId)
                .as("日付が読めないと、いつの請求か番号から分からない")
                .matches("INV-\\d{8}-[0-9a-f]{8}");
        assertThat(invoiceId.length())
                .as("invoice_id は VARCHAR(36)。超えると投影だけが退避される")
                .isLessThanOrEqualTo(36);
    }

    @Test
    @DisplayName("作れなかった事実が、経理の要確認一覧（S70）から読める")
    void theFailureIsReadableFromTheAttentionList() {
        // **記録するだけでは誰にも見えない**（IT3 で routingms が踏んだ形）。
        // IT13 は要確認へ書き始めたが、読み口を置き忘れていた——受け入れテストが
        // DB を直接読んでいたため、全緑のまま欠落が隠れていた（レビューで発見）。
        var fixture = cargo(null, true, true, "CORPORATE", "0.1500");

        deliver(fixture);

        var rest = org.springframework.web.client.RestClient.create();
        var response = rest.get()
                .uri("http://localhost:" + port + "/api/v1/billing/attention-items")
                .header("X-Auth-Roles", "ROLE_ACCOUNTANT")
                .retrieve()
                .toEntity(new org.springframework.core.ParameterizedTypeReference<
                        java.util.Map<String, Object>>() { });

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        var items = (List<java.util.Map<String, Object>>) response.getBody().get("items");
        assertThat(items)
                .filteredOn(item -> fixture.bookingId().equals(item.get("targetId")))
                .singleElement()
                .satisfies(item -> assertThat(String.valueOf(item.get("reason")))
                        .contains("重量"));
    }

    @Test
    @DisplayName("他ロールには経理宛の要確認を出さない（全員に見えるものは誰も直さない）")
    void doesNotShowAccountantItemsToOtherRoles() {
        var fixture = cargo(null, true, true, "CORPORATE", "0.1500");
        deliver(fixture);

        var rest = org.springframework.web.client.RestClient.create();
        var response = rest.get()
                .uri("http://localhost:" + port + "/api/v1/billing/attention-items")
                .header("X-Auth-Roles", "ROLE_TRACKER")
                .retrieve()
                .toEntity(new org.springframework.core.ParameterizedTypeReference<
                        java.util.Map<String, Object>>() { });

        @SuppressWarnings("unchecked")
        var items = (List<java.util.Map<String, Object>>) response.getBody().get("items");
        assertThat(items)
                .filteredOn(item -> fixture.bookingId().equals(item.get("targetId")))
                .isEmpty();
    }

    /** 作り直しの入口を叩く。**経理として**送る。 */
    private org.springframework.http.ResponseEntity<java.util.Map<String, Object>> recalculate(
            String bookingId) {
        return org.springframework.web.client.RestClient.builder()
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build()
                .post().uri("http://localhost:" + port + "/api/v1/billing/invoices/recalculate")
                .header("X-Auth-Roles", "ROLE_ACCOUNTANT")
                .header("X-Auth-Username", "acct01")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(java.util.Map.of("bookingId", bookingId))
                .retrieve()
                .toEntity(new org.springframework.core.ParameterizedTypeReference<
                        java.util.Map<String, Object>>() { });
    }

    @Test
    @DisplayName("引き継ぎ B: 材料を直したあとに請求を作り直せる（要確認も片づく）")
    void recalculatesAfterTheMaterialIsFixed() {
        // 重量が届いていなかったので請求書が作れず、要確認に出ている。
        var fixture = cargo(null, true, true, "CORPORATE", "0.1500");
        deliver(fixture);
        assertThat(attentionFor(fixture.bookingId())).hasSize(1);

        // 材料が直る（重量を運ぶイベントが届き、写しが上書きされる）。
        cargoProjection.on(new TrackingInitializedEvent(fixture.trackingNumber(),
                fixture.bookingId(), fixture.shipperId(), "JPTYO", "USNYC", "GENERAL",
                new BigDecimal("1200"),
                List.of(new TrackingInitializedEvent.Leg("V-MOL-001", "JPTYO", "SGSIN",
                                AT, AT.plusSeconds(86_400)),
                        new TrackingInitializedEvent.Leg("V-ONE-002", "SGSIN", "USNYC",
                                AT.plusSeconds(90_000), AT.plusSeconds(600_000))),
                AT), "evt-fixed-" + System.nanoTime());

        // **直しただけでは請求に戻らない。** 引取はもう届かないので、誰かが
        // 戻さなければ締めの母集団から落ち続ける。
        assertThat(queries.handle(new FindInvoiceOfBookingQuery(fixture.bookingId()))).isNull();

        var response = recalculate(fixture.bookingId());

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        assertThat(response.getBody().get("invoiceId")).isNotNull();
        assertThat(awaitInvoice(fixture.bookingId()).baseAmount())
                .as("連鎖と同じ材料・同じ式で作る")
                .isEqualByComparingTo("510000");
        assertThat(attentionFor(fixture.bookingId()))
                .as("片づいたものを毎朝の一覧に出し続けない")
                .isEmpty();
    }

    @Test
    @DisplayName("引き継ぎ B: 材料が直っていなければ、理由を返して作らない")
    void refusesToRecalculateWhileTheMaterialIsStillMissing() {
        var fixture = cargo(null, true, true, "CORPORATE", "0.1500");
        deliver(fixture);

        var response = recalculate(fixture.bookingId());

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
        assertThat(String.valueOf(response.getBody()))
                .as("何を直せばよいか分からないと、同じ操作が繰り返される")
                .contains("重量");
        assertThat(attentionFor(fixture.bookingId()))
                .as("作れていないのに片づけない")
                .hasSize(1);
    }

    @Test
    @DisplayName("引き継ぎ B: 要確認に出ていない予約は作り直せない（手で始める入口にしない）")
    void refusesToRecalculateABookingThatIsNotInTheAttentionList() {
        var fixture = cargo(new BigDecimal("1200"), true, true, "CORPORATE", "0.1500");

        var response = recalculate(fixture.bookingId());

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
        assertThat(queries.handle(new FindInvoiceOfBookingQuery(fixture.bookingId())))
                .as("連鎖が止まっていることを手で回して隠さない")
                .isNull();
    }

    @Test
    @DisplayName("ロールが伝わらなければ何も出さない（既定を置くと他ロール宛が見える）")
    void showsNothingWhenNoRoleIsPassed() {
        // **既定を置かない。** ロールの伝達が壊れていることに気づかないまま、
        // 他ロール宛の要確認が見えるほうが困る（bookingms・routingms と同じ形）。
        var fixture = cargo(null, true, true, "CORPORATE", "0.1500");
        deliver(fixture);

        var rest = org.springframework.web.client.RestClient.create();
        for (String header : new String[] {"", "  ,  "}) {
            var response = rest.get()
                    .uri("http://localhost:" + port + "/api/v1/billing/attention-items")
                    .header("X-Auth-Roles", header)
                    .retrieve()
                    .toEntity(new org.springframework.core.ParameterizedTypeReference<
                            java.util.Map<String, Object>>() { });

            @SuppressWarnings("unchecked")
            var items = (List<java.util.Map<String, Object>>) response.getBody().get("items");
            assertThat(items).as("ロール『%s』では何も出さない", header).isEmpty();
        }
    }
}
