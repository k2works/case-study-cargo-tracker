package com.example.cargotracker.billing.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.billing.domain.model.commands.AdjustInvoiceCommand;
import com.example.cargotracker.billing.domain.model.commands.CalculateInvoiceCommand;
import com.example.cargotracker.billing.domain.model.commands.IssueInvoiceCommand;
import com.example.cargotracker.billing.domain.model.commands.RecordPaymentCommand;
import com.example.cargotracker.billing.domain.model.commands.ReverseAdjustmentCommand;
import com.example.cargotracker.billing.domain.model.commands.VoidInvoiceCommand;
import com.example.cargotracker.billing.domain.model.events.InvoiceAdjustedEvent;
import com.example.cargotracker.billing.domain.model.events.InvoiceCalculatedEvent;
import com.example.cargotracker.billing.domain.model.valueobjects.DiscountRate;
import com.example.cargotracker.billing.domain.model.valueobjects.RateTableFixture;
import com.example.cargotracker.billing.domain.model.valueobjects.ShipperType;
import com.example.cargotracker.billing.domain.model.valueobjects.TransportRecord;
import com.example.cargotracker.billing.domain.service.DiscountPolicy;
import com.example.cargotracker.billing.domain.service.FreightChargeCalculator;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
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
 * 知らない請求書への操作と、識別子の導き方（UC17・UC18）。
 *
 * <p><b>復元されていない集約に操作が届く。</b> 画面の打ち間違い、連鎖の取り違え、
 * 投影が追いつく前の操作——いずれも起こる。<b>「請求書 X がありません」と断る</b>
 * のであって、空の請求書に操作が通ってはならない。</p>
 *
 * <p>正常系は {@code InvoiceTest} が見る。ここは<b>断り方</b>だけを見る。</p>
 */
class InvoiceGuardsTest {

    private static final Instant NOW = Instant.parse("2026-09-28T01:00:00Z");
    private static final String INVOICE = "INV-2026-0928-011";
    private static final String BOOKING = "B-2026-0902-004";

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

    /** JPTYO →(国内 1.0) JPOSA・1,200 kg・一般。**国内なので税が乗る。** */
    private static TransportRecord domesticTransport() {
        return new TransportRecord(
                List.of(new TransportRecord.BilledLeg(new UnLocode("JPTYO"),
                        new UnLocode("JPOSA"))),
                new BigDecimal("1200"), "GENERAL", new UnLocode("JPTYO"),
                new UnLocode("JPOSA"));
    }

    private static CalculateInvoiceCommand calculateDomestic() {
        return new CalculateInvoiceCommand(INVOICE, BOOKING, "SHP-000001", "山田商事",
                ShipperType.INDIVIDUAL, DiscountRate.none(), null, domesticTransport(),
                null, "accountant01");
    }

    private InvoiceCalculatedEvent calculatedEventOf(CalculateInvoiceCommand command) {
        var captured = new InvoiceCalculatedEvent[1];
        fixture.given().noPriorActivity()
                .when().command(command)
                .then().eventsSatisfy(events -> captured[0] = events.stream()
                        .map(event -> event.payload())
                        .filter(InvoiceCalculatedEvent.class::isInstance)
                        .map(InvoiceCalculatedEvent.class::cast)
                        .findFirst().orElseThrow());
        return captured[0];
    }

    @Test
    @DisplayName("知らない請求書は発行できない（空の請求書に操作を通さない）")
    void refusesToIssueAnUnknownInvoice() {
        fixture.given().noPriorActivity()
                .when().command(new IssueInvoiceCommand(INVOICE, "accountant01"))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("がありません"));
    }

    @Test
    @DisplayName("知らない請求書に入金は記録できない")
    void refusesToRecordPaymentForAnUnknownInvoice() {
        fixture.given().noPriorActivity()
                .when().command(new RecordPaymentCommand(INVOICE, "PAY-1",
                        new BigDecimal("1"), NOW, "accountant01"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("知らない請求書は取り消せない")
    void refusesToVoidAnUnknownInvoice() {
        fixture.given().noPriorActivity()
                .when().command(new VoidInvoiceCommand(INVOICE, "宛先の誤り", "accountant01"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("知らない請求書の調整は取り消せない")
    void refusesToReverseOnAnUnknownInvoice() {
        fixture.given().noPriorActivity()
                .when().command(new ReverseAdjustmentCommand(INVOICE, "ADJ-1", "誤入力",
                        "accountant01"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("知らない調整は取り消せない（入れていない調整を戻さない）")
    void refusesToReverseAnUnknownAdjustment() {
        var calculated = calculatedEventOf(calculateDomestic());

        fixture.given().event(calculated)
                .when().command(new ReverseAdjustmentCommand(INVOICE, "ADJ-NOT-THERE",
                        "誤入力", "accountant01"))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("ADJ-NOT-THERE"));
    }

    @Test
    @DisplayName("接頭辞の違う調整の取り消しも 36 文字に収まる（切り詰めて衝突させない）")
    void derivesAReversalIdFromAnyAdjustmentId() {
        var calculated = calculatedEventOf(calculateDomestic());
        // **"ADJ-" で始まらない古い形の識別子**。末尾 32 文字に収めて接頭辞を足す。
        String legacyId = "LEGACY-" + "0123456789abcdef0123456789abcdef";
        var adjusted = new InvoiceAdjustedEvent(INVOICE, legacyId, null,
                new BigDecimal("-1000"), "誤配の補償", "EX-1",
                new BigDecimal("-1000"), new BigDecimal("5900"),
                new BigDecimal("64900"), "JPY", "accountant01", NOW);

        fixture.given().event(calculated).event(adjusted)
                .when().command(new ReverseAdjustmentCommand(INVOICE, legacyId, "誤入力",
                        "accountant01"))
                .then().success()
                .eventsSatisfy(events -> assertThat(events).anySatisfy(event -> {
                    var payload = (InvoiceAdjustedEvent) event.payload();
                    assertThat(payload.adjustmentId())
                            .as("列は VARCHAR(36)。あふれると投影だけが静かに退避される")
                            .hasSizeLessThanOrEqualTo(36)
                            .startsWith("REV-");
                }));
    }

    @Test
    @DisplayName("国内輸送では調整後の合計に税が乗る（免税と同じ数え方にしない）")
    void addsTaxToTheDomesticTotal() {
        var calculated = calculatedEventOf(calculateDomestic());

        fixture.given().event(calculated)
                .when().command(new AdjustInvoiceCommand(INVOICE, "ADJ-1",
                        new BigDecimal("10000"), "留置の保管料", "IMP-1", "accountant01"))
                .then().success()
                .eventsSatisfy(events -> assertThat(events).anySatisfy(event -> {
                    var payload = (InvoiceAdjustedEvent) event.payload();
                    // 基本 60,000 + 調整 10,000 = 70,000 に 10% で 77,000。
                    assertThat(payload.totalAmount())
                            .as("免税と同じに数えると、国内の請求が税の分だけ足りなくなる")
                            .isEqualByComparingTo("77000");
                }));
    }
}
