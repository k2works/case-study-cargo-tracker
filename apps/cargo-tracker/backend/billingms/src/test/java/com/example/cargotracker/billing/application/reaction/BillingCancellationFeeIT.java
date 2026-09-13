package com.example.cargotracker.billing.application.reaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.billing.infrastructure.persistence.AttentionItemMapper;
import com.example.cargotracker.billing.infrastructure.projection.BillingCargoProjection;
import com.example.cargotracker.billing.infrastructure.projection.ShipperContractProjection;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries
        .FindInvoiceOfBookingQuery;
import com.example.cargotracker.billing.infrastructure.query.InvoiceQueryHandler;
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
 * キャンセル料の連鎖（UC22 / US30 §受入基準 9。IT15 T8）。
 *
 * <p><b>{@code BillingReactionHandlerIT} から分けた。</b> 1 ファイルが 500 行を
 * 超えると、何を確かめているファイルなのかが読めなくなる。引取からの算出と
 * キャンセル料は<b>別の連鎖</b>なので、切り口もそこに置いた。</p>
 *
 * <p><b>請求書がまだ無いのが通常の経路である</b>（正典「キャンセル料の受け皿」）。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BillingCancellationFeeIT extends AbstractAxonIntegrationTest {

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

    private record Fixture(String trackingNumber, String bookingId, String shipperId) {
    }

    /** 請求の材料をそろえる（{@code BillingReactionHandlerIT} と同じ形）。 */
    private Fixture cargo(BigDecimal weightKg, boolean withLegs, boolean withContract,
            String shipperType, String discountRate) {
        String suffix = String.valueOf(System.nanoTime());
        String trackingNumber = "TRK-CF" + suffix.substring(suffix.length() - 9);
        String bookingId = "B-CF-" + suffix;
        String shipperId = "SHP-CF-" + suffix;

        if (withContract) {
            shipperProjection.on(new ShipperRegisteredEvent(shipperId, shipperType, "山田商事",
                    shipperId + "@example.com", "03-0000-0000", "東京都港区",
                    discountRate == null ? null : "CT-0012", discountRate));
        }
        cargoProjection.on(new TrackingInitializedEvent(trackingNumber, bookingId, shipperId,
                "JPTYO", "USNYC", "GENERAL", weightKg,
                withLegs
                        ? List.of(new TrackingInitializedEvent.Leg("V-MOL-001", "JPTYO",
                                        "SGSIN", AT, AT.plusSeconds(86_400)),
                                new TrackingInitializedEvent.Leg("V-ONE-002", "SGSIN",
                                        "USNYC", AT.plusSeconds(90_000),
                                        AT.plusSeconds(600_000)))
                        : List.of(),
                AT), "evt-cf-" + suffix);
        return new Fixture(trackingNumber, bookingId, shipperId);
    }

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
    @DisplayName("US30 §9: キャンセル料だけの請求書ができる（IT15 T8）")
    void createsAnInvoiceForTheCancellationFee() {
        // **請求書がまだ無いのが通常の経路。** 輸送は行われていないので輸送料金は
        // 無く、請求するのはキャンセル料だけである（正典「キャンセル料の受け皿」）。
        var fixture = cargo(new BigDecimal("1200"), true, true, "INDIVIDUAL", null);

        handler.on(new com.example.cargotracker.shared.contract.event.CargoCancelledEvent(
                fixture.bookingId(), fixture.trackingNumber(), "IN_TRANSIT", "SGSIN",
                "荷主の発注取消", "tracker01", AT));

        // 投影は非同期。**書かれるまで待つ**（他の検査と同じ形）。
        assertThat(awaitInvoice(fixture.bookingId()))
                .satisfies(invoice -> {
                    assertThat(invoice.totalAmount())
                            .as("510,000 × 50% + 消費税（輸出免税なので 0）")
                            .isEqualByComparingTo("255000");
                    assertThat(invoice.lineItems())
                            .extracting(line -> line.itemType())
                            .as("基本料金の行は出さない——輸送していない")
                            .containsExactly("CANCELLATION_FEE", "TAX");
                });
    }

    @Test
    @DisplayName("仮受付のキャンセルでは請求書を作らない（0 円の請求書は存在しない）")
    void createsNothingWhenTheFeeIsZero() {
        var fixture = cargo(new BigDecimal("1200"), true, true, "INDIVIDUAL", null);

        handler.on(new com.example.cargotracker.shared.contract.event.CargoCancelledEvent(
                fixture.bookingId(), null, "PRELIMINARY", null, "荷主の発注取消",
                "sales01", AT));

        assertThat(queries.handle(new FindInvoiceOfBookingQuery(fixture.bookingId())))
                .as("記録だけ作ると、経理が毎朝「確かめるもの」として読む")
                .isNull();
    }

    @Test
    @DisplayName("写しが無ければ経理宛の要確認に出す（黙って 0 円にしない）")
    void raisesAttentionWhenTheSnapshotIsMissing() {
        String bookingId = "B-NOCARGO-" + System.nanoTime();

        handler.on(new com.example.cargotracker.shared.contract.event.CargoCancelledEvent(
                bookingId, null, "CONFIRMED", null, "荷主の発注取消", "sales01", AT));

        await().atMost(Duration.ofSeconds(30))
                .until(() -> !attentionFor(bookingId).isEmpty());
        assertThat(attentionFor(bookingId))
                .as("取りこぼした請求はあとから取り返せない")
                .isNotEmpty();
    }

    @Test
    @DisplayName("重量が分からない貨物のキャンセル料は要確認へ（待っても入らない）")
    void raisesAttentionWhenTheWeightIsUnknown() {
        var fixture = cargo(null, true, true, "INDIVIDUAL", null);

        handler.on(new com.example.cargotracker.shared.contract.event.CargoCancelledEvent(
                fixture.bookingId(), fixture.trackingNumber(), "IN_TRANSIT", "SGSIN",
                "荷主の発注取消", "tracker01", AT));

        await().atMost(Duration.ofSeconds(30))
                .until(() -> !attentionFor(fixture.bookingId()).isEmpty());
        assertThat(attentionFor(fixture.bookingId())).isNotEmpty();
    }

    @Test
    @DisplayName("区間が分からない貨物のキャンセル料も要確認へ")
    void raisesAttentionWhenTheLegsAreMissing() {
        var fixture = cargo(new BigDecimal("1200"), false, true, "INDIVIDUAL", null);

        handler.on(new com.example.cargotracker.shared.contract.event.CargoCancelledEvent(
                fixture.bookingId(), fixture.trackingNumber(), "IN_TRANSIT", "SGSIN",
                "荷主の発注取消", "tracker01", AT));

        await().atMost(Duration.ofSeconds(30))
                .until(() -> !attentionFor(fixture.bookingId()).isEmpty());
        assertThat(attentionFor(fixture.bookingId())).isNotEmpty();
    }

    @Test
    @DisplayName("荷主の契約が届いていなければ投げ直す（待てば入る）")
    void rethrowsWhenTheContractHasNotArrived() {
        var fixture = cargo(new BigDecimal("1200"), true, false, "INDIVIDUAL", null);

        assertThatThrownBy(() -> handler.on(new com.example.cargotracker.shared.contract
                .event.CargoCancelledEvent(fixture.bookingId(), fixture.trackingNumber(),
                "IN_TRANSIT", "SGSIN", "荷主の発注取消", "tracker01", AT)))
                .as("購読の遅れは退避先が受け止め、処理し直せる")
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("すでに請求書がある予約のキャンセル料は、自動で足さず要確認へ")
    void raisesAttentionWhenAnInvoiceAlreadyExists() {
        // **調整として積むのは経理の判断。** 自動で足すと、誤って二重に請求する。
        var fixture = cargo(new BigDecimal("1200"), true, true, "INDIVIDUAL", null);
        handler.on(new com.example.cargotracker.shared.contract.event.CargoDeliveredEvent(
                fixture.trackingNumber(), fixture.bookingId(), AT, "USNYC"));
        awaitInvoice(fixture.bookingId());

        handler.on(new com.example.cargotracker.shared.contract.event.CargoCancelledEvent(
                fixture.bookingId(), fixture.trackingNumber(), "IN_TRANSIT", "SGSIN",
                "荷主の発注取消", "tracker01", AT));

        await().atMost(Duration.ofSeconds(30))
                .until(() -> !attentionFor(fixture.bookingId()).isEmpty());
        assertThat(attentionFor(fixture.bookingId())).isNotEmpty();
    }
}
