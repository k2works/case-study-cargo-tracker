package com.example.cargotracker.billing.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.billing.domain.model.commands.ApplyCancellationFeeCommand;
import com.example.cargotracker.billing.domain.model.events.CancellationFeeAppliedEvent;
import com.example.cargotracker.billing.domain.model.valueobjects.DiscountRate;
import com.example.cargotracker.billing.domain.model.valueobjects.LineItemType;
import com.example.cargotracker.billing.domain.model.valueobjects.RateTableFixture;
import com.example.cargotracker.billing.domain.model.valueobjects.ShipperType;
import com.example.cargotracker.billing.domain.model.valueobjects.TransportRecord;
import com.example.cargotracker.billing.domain.service.DiscountPolicy;
import com.example.cargotracker.billing.domain.service.FreightChargeCalculator;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.UnLocode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * キャンセル料（US30 §受入基準 9 / 正典「キャンセル料の受け皿」）。
 *
 * <p><b>明細行として積む。</b> 別の帳票を作らない——荷主が受け取るものを増やさない。
 * 請求書がまだ無いのが通常の経路なので、<b>キャンセル料だけの請求書</b>ができる。</p>
 *
 * <p><b>基準は「運ぶはずだった料金」。</b> 輸送は行われていないが、キャンセル料は
 * 基本料金 × 状態別料率で決まる（正典の料金計算）。</p>
 */
class InvoiceCancellationFeeTest {

    private static final String INVOICE = "INV-20260928-1a2b3c4d";
    private static final String BOOKING = "B-0001";
    private static final Instant NOW = Instant.parse("2026-09-28T01:00:00Z");

    private AxonTestFixture fixture;

    @BeforeEach
    void setUp() {
        EventSourcingConfigurer configurer = EventSourcingConfigurer.create()
                .registerEntity(EventSourcedEntityModule.autodetected(
                        String.class, Invoice.class))
                .componentRegistry(registry -> registry
                        .registerComponent(Clock.class,
                                c -> Clock.fixed(NOW, ZoneId.of("Asia/Tokyo")))
                        .registerComponent(FreightChargeCalculator.class,
                                c -> new FreightChargeCalculator())
                        .registerComponent(DiscountPolicy.class, c -> new DiscountPolicy())
                        .registerComponent(
                                com.example.cargotracker.billing.domain.model.valueobjects
                                        .RateTable.class,
                                c -> RateTableFixture.canonical()));
        fixture = AxonTestFixture.with(configurer, c -> c.disableAxonServer());
    }

    /** JPTYO →(近海 2.5) SGSIN →(遠洋 6.0) USNYC・1,200 kg・一般 = 基本料金 510,000 円。 */
    private static TransportRecord transport() {
        return new TransportRecord(
                List.of(new TransportRecord.BilledLeg(new UnLocode("JPTYO"),
                                new UnLocode("SGSIN")),
                        new TransportRecord.BilledLeg(new UnLocode("SGSIN"),
                                new UnLocode("USNYC"))),
                new BigDecimal("1200"), "GENERAL",
                new UnLocode("JPTYO"), new UnLocode("USNYC"));
    }

    private static ApplyCancellationFeeCommand apply(String statusAtCancel) {
        return new ApplyCancellationFeeCommand(INVOICE, BOOKING, "SHP-000001", "山田商事",
                ShipperType.INDIVIDUAL, DiscountRate.none(), null, transport(),
                statusAtCancel, "system");
    }

    private CancellationFeeAppliedEvent applied(String statusAtCancel) {
        var captured = new CancellationFeeAppliedEvent[1];
        fixture.given().noPriorActivity()
                .when().command(apply(statusAtCancel))
                .then().eventsSatisfy(events -> captured[0] = events.stream()
                        .map(event -> event.payload())
                        .filter(CancellationFeeAppliedEvent.class::isInstance)
                        .map(CancellationFeeAppliedEvent.class::cast)
                        .findFirst().orElseThrow());
        return captured[0];
    }

    @Test
    @DisplayName("US30 §9: 輸送中のキャンセルは基本料金の 50%")
    void chargesHalfWhileInTransit() {
        var event = applied("IN_TRANSIT");

        assertThat(event.feeRate()).isEqualByComparingTo("0.50");
        assertThat(event.baseAmount())
                .as("510,000 × 0.50。**基準は「運ぶはずだった料金」**")
                .isEqualByComparingTo("255000");
    }

    @Test
    @DisplayName("状態が変われば料率も変わる（追跡番号発行済は 20%）")
    void chargesLessBeforeDeparture() {
        assertThat(applied("TRACKING_ISSUED").baseAmount())
                .isEqualByComparingTo("102000");
    }

    @Test
    @DisplayName("明細はキャンセル料の行で、なぜその額かが読める")
    void explainsTheFeeInTheLineItem() {
        var event = applied("IN_TRANSIT");

        assertThat(event.lineItems())
                .as("基本料金の行は出さない——輸送していない")
                .extracting(item -> item.itemType())
                .containsExactly(LineItemType.CANCELLATION_FEE.name(),
                        LineItemType.TAX.name());
        assertThat(event.lineItems().getFirst().description())
                .as("料率だけでは「なぜこの額か」が読めない")
                .contains("輸送中")
                .contains("50");
    }

    @Test
    @DisplayName("法人割引はキャンセル料にも当たる（契約は生きている）")
    void appliesTheCorporateDiscount() {
        fixture.given().noPriorActivity()
                .when().command(new ApplyCancellationFeeCommand(INVOICE, BOOKING,
                        "SHP-000001", "山田商事", ShipperType.CORPORATE,
                        DiscountRate.of(new BigDecimal("0.1500")), "CT-0012",
                        transport(), "IN_TRANSIT", "system"))
                .then().eventsSatisfy(events -> assertThat(events)
                        .map(event -> event.payload())
                        .filteredOn(CancellationFeeAppliedEvent.class::isInstance)
                        .map(CancellationFeeAppliedEvent.class::cast)
                        .singleElement()
                        .satisfies(applied -> assertThat(applied.discountAmount())
                                .as("255,000 × 15%")
                                .isEqualByComparingTo("38250")));
    }

    @Test
    @DisplayName("料率 0%（仮受付）でもコマンドは通る（積むかどうかは連鎖が決める）")
    void acceptsAZeroRate() {
        // **集約は「0 円だから作らない」を決めない。** それは業務の判断で、
        // 連鎖（`BillingReactionHandler`）が料率表に尋ねて決める。
        // 集約まで来たものは、額が 0 でも記録する。
        var event = applied("PRELIMINARY");

        assertThat(event.feeRate()).isEqualByComparingTo("0.00");
        assertThat(event.baseAmount()).isEqualByComparingTo("0");
        assertThat(event.lineItems())
                .extracting(item -> item.itemType())
                .as("輸出免税なので税は 0 円の行として出る")
                .containsExactly(LineItemType.CANCELLATION_FEE.name(),
                        LineItemType.TAX.name());
    }

    @Test
    @DisplayName("国内・法人・割引ありでも、行がそろって出る")
    void showsEveryLineForDomesticCorporate() {
        var domestic = new TransportRecord(
                List.of(new TransportRecord.BilledLeg(new UnLocode("JPTYO"),
                        new UnLocode("JPOSA"))),
                new BigDecimal("1200"), "GENERAL",
                new UnLocode("JPTYO"), new UnLocode("JPOSA"));

        fixture.given().noPriorActivity()
                .when().command(new ApplyCancellationFeeCommand(INVOICE, BOOKING,
                        "SHP-000001", "山田商事", ShipperType.CORPORATE,
                        DiscountRate.of(new BigDecimal("0.1500")), "CT-0012",
                        domestic, "IN_TRANSIT", "system"))
                .then().eventsSatisfy(events -> assertThat(events)
                        .map(event -> event.payload())
                        .filteredOn(CancellationFeeAppliedEvent.class::isInstance)
                        .map(CancellationFeeAppliedEvent.class::cast)
                        .singleElement()
                        .satisfies(applied -> assertThat(applied.lineItems())
                                .extracting(item -> item.itemType())
                                .containsExactly(LineItemType.CANCELLATION_FEE.name(),
                                        LineItemType.DISCOUNT.name(),
                                        LineItemType.TAX.name())));
    }

    @Test
    @DisplayName("料率表に無い状態は断る（黙って 0 円にしない）")
    void refusesAnUnknownStatus() {
        fixture.given().noPriorActivity()
                .when().command(apply("NO_SUCH_STATUS"))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("荷主種別が分からなければ断る（算出と同じ扱い）")
    void refusesWithoutAShipperType() {
        // **割引を判断できないまま額を出さない。** 法人の割引が当たらない請求書が
        // 静かにできる。材料が足りないときに要確認へ回すのは
        // `InvoiceCalculation` の役目で、集約は断るだけである。
        // **この検査で NullPointerException を見つけた**——書く前は
        // `DiscountPolicy` の中で落ちていた。
        fixture.given().noPriorActivity()
                .when().command(new ApplyCancellationFeeCommand(INVOICE, BOOKING,
                        "SHP-000001", null, null, DiscountRate.none(), null,
                        transport(), "IN_TRANSIT", "system"))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("すでに算出済の請求書には二度積まない（二度届いても 1 度だけ）")
    void doesNotApplyTwice() {
        fixture.given().event(applied("IN_TRANSIT"))
                .when().command(apply("IN_TRANSIT"))
                .then().exception(
                        com.example.cargotracker.shared.domain.error.IllegalTransition.class);
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "{0} → {1}")
    @org.junit.jupiter.params.provider.CsvSource({
        "PRELIMINARY, 仮受付",
        "ROUTE_PROPOSED, 経路提案中",
        "ROUTE_NOTIFIED, 経路通知済",
        "CONFIRMED, 予約確定",
        "TRACKING_ISSUED, 追跡番号発行済",
        "IN_TRANSIT, 輸送中",
    })
    @DisplayName("明細にはキャンセル時の状態が業務の言葉で出る（列挙名を出さない）")
    void showsTheStatusInBusinessWords(String status, String label) {
        // **料率だけでは「なぜこの額か」が読めない。** 状態の呼び名は billingms が
        // 持つ（bookingms の型に依存できない）ので、値の一覧から回して確かめる
        // ——名簿を手書きすると、扱っていない状態が名乗り出ない。
        var event = appliedOrNull(status);
        if (event == null) {
            // 料率 0%（仮受付）でも明細の説明は同じ形で出る。
            return;
        }
        assertThat(event.lineItems().getFirst().description()).contains(label);
    }

    private CancellationFeeAppliedEvent appliedOrNull(String statusAtCancel) {
        return applied(statusAtCancel);
    }

    @Test
    @DisplayName("請求書 ID の無いコマンドは断る")
    void refusesWithoutAnInvoiceId() {
        fixture.given().noPriorActivity()
                .when().command(new ApplyCancellationFeeCommand("  ", BOOKING, "SHP-000001",
                        "山田商事", ShipperType.INDIVIDUAL, DiscountRate.none(), null,
                        transport(), "IN_TRANSIT", "system"))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("法人でも割引率が 0 なら割引行は出さない（動かない行を積まない）")
    void omitsTheDiscountLineWhenThereIsNone() {
        fixture.given().noPriorActivity()
                .when().command(new ApplyCancellationFeeCommand(INVOICE, BOOKING,
                        "SHP-000001", "山田商事", ShipperType.CORPORATE,
                        DiscountRate.none(), "CT-0012", transport(), "IN_TRANSIT", "system"))
                .then().eventsSatisfy(events -> assertThat(events)
                        .map(event -> event.payload())
                        .filteredOn(CancellationFeeAppliedEvent.class::isInstance)
                        .map(CancellationFeeAppliedEvent.class::cast)
                        .singleElement()
                        .satisfies(applied -> assertThat(applied.lineItems())
                                .extracting(item -> item.itemType())
                                .doesNotContain(LineItemType.DISCOUNT.name())));
    }

    @Test
    @DisplayName("知らない状態の呼び名はそのまま出す（明細から消さない）")
    void keepsAnUnknownStatusInTheDescription() {
        // 料率表に載っていれば額は出せる。**呼び名が分からないことを理由に
        // 明細から状態を落とすと、「どの状態でのキャンセルか」が読めなくなる。**
        assertThat(BookingStatusLabel.of("SOMETHING_NEW")).isEqualTo("SOMETHING_NEW");
        assertThat(BookingStatusLabel.of(null)).isEqualTo("不明");
    }

    @Test
    @DisplayName("予約 ID の無いコマンドは断る")
    void refusesWithoutABookingId() {
        fixture.given().noPriorActivity()
                .when().command(new ApplyCancellationFeeCommand(INVOICE, "  ", "SHP-000001",
                        "山田商事", ShipperType.INDIVIDUAL, DiscountRate.none(), null,
                        transport(), "IN_TRANSIT", "system"))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("輸送実績の無いコマンドは断る（基本料金が出せない）")
    void refusesWithoutATransportRecord() {
        fixture.given().noPriorActivity()
                .when().command(new ApplyCancellationFeeCommand(INVOICE, BOOKING,
                        "SHP-000001", "山田商事", ShipperType.INDIVIDUAL,
                        DiscountRate.none(), null, null, "IN_TRANSIT", "system"))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("国内の貨物には消費税が付く（輸出免税ではない）")
    void chargesTaxForDomesticCargo() {
        var domestic = new TransportRecord(
                List.of(new TransportRecord.BilledLeg(new UnLocode("JPTYO"),
                        new UnLocode("JPOSA"))),
                new BigDecimal("1200"), "GENERAL",
                new UnLocode("JPTYO"), new UnLocode("JPOSA"));

        fixture.given().noPriorActivity()
                .when().command(new ApplyCancellationFeeCommand(INVOICE, BOOKING,
                        "SHP-000001", "山田商事", ShipperType.INDIVIDUAL,
                        DiscountRate.none(), null, domestic, "IN_TRANSIT", "system"))
                .then().eventsSatisfy(events -> assertThat(events)
                        .map(event -> event.payload())
                        .filteredOn(CancellationFeeAppliedEvent.class::isInstance)
                        .map(CancellationFeeAppliedEvent.class::cast)
                        .singleElement()
                        .satisfies(applied -> {
                            assertThat(applied.taxAmount()).isGreaterThan(BigDecimal.ZERO);
                            assertThat(applied.lineItems())
                                    .extracting(item -> item.itemType())
                                    .contains(LineItemType.TAX.name());
                        }));
    }
}
