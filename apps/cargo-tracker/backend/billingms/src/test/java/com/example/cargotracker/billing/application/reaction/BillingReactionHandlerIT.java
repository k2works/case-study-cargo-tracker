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
@SpringBootTest
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
}
