package com.example.cargotracker.billing.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.billing.domain.model.commands.AdjustInvoiceCommand;
import com.example.cargotracker.billing.domain.model.commands.CalculateInvoiceCommand;
import com.example.cargotracker.billing.domain.model.events.InvoiceAdjustedEvent;
import com.example.cargotracker.billing.domain.model.events.InvoiceCalculatedEvent;
import com.example.cargotracker.billing.domain.model.valueobjects.DiscountRate;
import com.example.cargotracker.billing.domain.model.valueobjects.LineItemType;
import com.example.cargotracker.billing.domain.model.valueobjects.RateTableFixture;
import com.example.cargotracker.billing.domain.model.valueobjects.ShipperType;
import com.example.cargotracker.billing.domain.model.valueobjects.TransportRecord;
import com.example.cargotracker.billing.domain.service.DiscountPolicy;
import com.example.cargotracker.billing.domain.service.FreightChargeCalculator;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
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
 * 請求書 1 通（UC17 / US21・US22）。
 *
 * <p><b>算出できたときに初めて集約ができる。</b> 算出できない（重量が分からない）
 * 場合は請求書を作らず、要確認へ出す（連鎖側の仕事）。だから {@code PENDING} は
 * この版の経路では通らない。</p>
 */
class InvoiceTest {

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

    /** JPTYO →(近海 2.5) SGSIN →(遠洋 6.0) USNYC・1,200 kg・一般。 */
    private static TransportRecord transport() {
        return new TransportRecord(
                List.of(new TransportRecord.BilledLeg(new UnLocode("JPTYO"),
                                new UnLocode("SGSIN")),
                        new TransportRecord.BilledLeg(new UnLocode("SGSIN"),
                                new UnLocode("USNYC"))),
                new BigDecimal("1200"), "GENERAL", new UnLocode("JPTYO"), new UnLocode("USNYC"));
    }

    private static CalculateInvoiceCommand calculate(ShipperType type, String discountRate) {
        return new CalculateInvoiceCommand(INVOICE, BOOKING, "SHP-000001", "山田商事", type,
                discountRate == null ? DiscountRate.none()
                        : DiscountRate.of(new BigDecimal(discountRate)),
                "CT-0012", transport(), "accountant01");
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
    @DisplayName("US21 §3・§5: 算出すると基本料金つきで「算出済」になる")
    void calculatesTheBaseCharge() {
        InvoiceCalculatedEvent event = calculatedEventOf(calculate(ShipperType.INDIVIDUAL, null));

        // 50,000 × (2.5 + 6.0) × 1.2 × 1.0 = 510,000
        assertThat(event.baseAmount()).isEqualByComparingTo("510000");
        assertThat(event.discountAmount()).isEqualByComparingTo("0");
        // **輸出は免税**（JP → US）。
        assertThat(event.taxAmount()).isEqualByComparingTo("0");
        assertThat(event.totalAmount()).isEqualByComparingTo("510000");
    }

    @Test
    @DisplayName("US22 §1・§2・§4: 法人には割引が当たり、根拠が明細に出る")
    void appliesTheCorporateDiscount() {
        InvoiceCalculatedEvent event = calculatedEventOf(
                calculate(ShipperType.CORPORATE, "0.1500"));

        assertThat(event.discountAmount()).isEqualByComparingTo("76500");
        assertThat(event.totalAmount()).isEqualByComparingTo("433500");
        assertThat(event.discountRate()).isEqualByComparingTo("0.1500");
        assertThat(event.lineItems())
                .filteredOn(item -> LineItemType.DISCOUNT.name().equals(item.itemType()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.description())
                            .as("割引率と契約番号が読めなければ、根拠にならない（US22 §4）")
                            .contains("15").contains("CT-0012");
                    assertThat(item.amount()).isEqualByComparingTo("76500");
                });
    }

    @Test
    @DisplayName("US22 §3: 個人には割引が当たらない（率が入っていても）")
    void neverDiscountsIndividuals() {
        InvoiceCalculatedEvent event = calculatedEventOf(
                calculate(ShipperType.INDIVIDUAL, "0.1500"));

        assertThat(event.discountAmount()).isEqualByComparingTo("0");
        assertThat(event.lineItems())
                .noneMatch(item -> LineItemType.DISCOUNT.name().equals(item.itemType()));
    }

    @Test
    @DisplayName("US21 §2: 明細に基本料金の根拠が並ぶ（区間・地域区分・重量・種別）")
    void putsTheBasisIntoTheLineItems() {
        InvoiceCalculatedEvent event = calculatedEventOf(calculate(ShipperType.INDIVIDUAL, null));

        assertThat(event.lineItems())
                .filteredOn(item -> LineItemType.BASE.name().equals(item.itemType()))
                .singleElement()
                .satisfies(item -> assertThat(item.description())
                        .contains("2 区間").contains("近海").contains("遠洋")
                        .contains("1,200 kg").contains("一般"));
    }

    @Test
    @DisplayName("国内輸送には消費税が付く（免税は輸出だけ）")
    void taxesDomesticShipments() {
        var domestic = new TransportRecord(
                List.of(new TransportRecord.BilledLeg(new UnLocode("JPTYO"),
                        new UnLocode("JPOSA"))),
                new BigDecimal("1000"), "GENERAL", new UnLocode("JPTYO"), new UnLocode("JPOSA"));
        var command = new CalculateInvoiceCommand(INVOICE, BOOKING, "SHP-000001", "山田商事",
                ShipperType.INDIVIDUAL, DiscountRate.none(), null, domestic, "accountant01");

        InvoiceCalculatedEvent event = calculatedEventOf(command);

        assertThat(event.baseAmount()).isEqualByComparingTo("50000");
        assertThat(event.taxAmount()).isEqualByComparingTo("5000");
        assertThat(event.totalAmount()).isEqualByComparingTo("55000");
        assertThat(event.lineItems())
                .anyMatch(item -> LineItemType.TAX.name().equals(item.itemType()));
    }

    @Test
    @DisplayName("不変条件 2: 同じ請求書に二度算出しない")
    void refusesSecondCalculation() {
        fixture.given().event(calculatedEventOf(calculate(ShipperType.INDIVIDUAL, null)))
                .when().command(calculate(ShipperType.INDIVIDUAL, null))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("US21 §6: 調整を入れると合計が動き、根拠の例外が残る")
    void acceptsAdjustmentWithItsBasis() {
        var calculated = calculatedEventOf(calculate(ShipperType.CORPORATE, "0.1500"));

        var captured = new InvoiceAdjustedEvent[1];
        fixture.given().event(calculated)
                .when().command(new AdjustInvoiceCommand(INVOICE, new BigDecimal("-10000"),
                        "遅延の補償", "EX-2026-0928-03", "accountant01"))
                .then().eventsSatisfy(events -> captured[0] = events.stream()
                        .map(event -> event.payload())
                        .filter(InvoiceAdjustedEvent.class::isInstance)
                        .map(InvoiceAdjustedEvent.class::cast)
                        .findFirst().orElseThrow());

        assertThat(captured[0].amount()).isEqualByComparingTo("-10000");
        assertThat(captured[0].basisExceptionId()).isEqualTo("EX-2026-0928-03");
        // 433,500 − 10,000 = 423,500（輸出免税なので税は 0 のまま）
        assertThat(captured[0].totalAmount()).isEqualByComparingTo("423500");
    }

    @Test
    @DisplayName("US21 §6: 調整には理由が要る（何が起きたか読めない記録を残さない）")
    void requiresAReasonForAdjustment() {
        var calculated = calculatedEventOf(calculate(ShipperType.INDIVIDUAL, null));

        fixture.given().event(calculated)
                .when().command(new AdjustInvoiceCommand(INVOICE, new BigDecimal("-10000"),
                        "  ", "EX-1", "accountant01"))
                .then().exception(BusinessRuleViolation.class);
        // 0 円の調整は履歴に意味の無い行を積む。
        fixture.given().event(calculated)
                .when().command(new AdjustInvoiceCommand(INVOICE, BigDecimal.ZERO,
                        "動かない調整", "EX-1", "accountant01"))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("不変条件 1: 合計が負になる調整は断る")
    void refusesAdjustmentBelowZero() {
        var calculated = calculatedEventOf(calculate(ShipperType.INDIVIDUAL, null));

        fixture.given().event(calculated)
                .when().command(new AdjustInvoiceCommand(INVOICE, new BigDecimal("-600000"),
                        "過大な減額", null, "accountant01"))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("算出していない請求書は調整できない")
    void refusesAdjustmentBeforeCalculation() {
        fixture.given().noPriorActivity()
                .when().command(new AdjustInvoiceCommand(INVOICE, new BigDecimal("-1000"),
                        "理由", null, "accountant01"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("算出の材料が欠けていれば断る（誰の・どの予約か分からない請求書を作らない）")
    void refusesIncompleteInput() {
        var transport = transport();
        fixture.given().noPriorActivity()
                .when().command(new CalculateInvoiceCommand(INVOICE, "  ", "SHP-000001", "山田商事",
                        ShipperType.INDIVIDUAL, DiscountRate.none(), null, transport, "a01"))
                .then().exception(BusinessRuleViolation.class);
        fixture.given().noPriorActivity()
                .when().command(new CalculateInvoiceCommand(INVOICE, BOOKING, null, "山田商事",
                        ShipperType.INDIVIDUAL, DiscountRate.none(), null, transport, "a01"))
                .then().exception(BusinessRuleViolation.class);
        fixture.given().noPriorActivity()
                .when().command(new CalculateInvoiceCommand(INVOICE, BOOKING, "SHP-000001", null,
                        // 荷主種別が分からなければ割引を判断できない。
                        null, DiscountRate.none(), null, transport, "a01"))
                .then().exception(BusinessRuleViolation.class);
        fixture.given().noPriorActivity()
                .when().command(new CalculateInvoiceCommand(INVOICE, BOOKING, "SHP-000001", null,
                        ShipperType.INDIVIDUAL, DiscountRate.none(), null, null, "a01"))
                .then().exception(BusinessRuleViolation.class);
        fixture.given().noPriorActivity()
                .when().command(new CalculateInvoiceCommand(" ", BOOKING, "SHP-000001", null,
                        ShipperType.INDIVIDUAL, DiscountRate.none(), null, transport, "a01"))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("契約番号の無い法人でも割引行は出る（率だけで根拠になる）")
    void discountLineWorksWithoutContractNumber() {
        var command = new CalculateInvoiceCommand(INVOICE, BOOKING, "SHP-000001", "山田商事",
                ShipperType.CORPORATE, DiscountRate.of(new BigDecimal("0.1000")), null,
                transport(), "accountant01");

        InvoiceCalculatedEvent event = calculatedEventOf(command);

        assertThat(event.lineItems())
                .filteredOn(item -> LineItemType.DISCOUNT.name().equals(item.itemType()))
                .singleElement()
                .satisfies(item -> assertThat(item.description()).contains("10"));
    }

    @Test
    @DisplayName("調整した人が分からない調整は断る")
    void requiresWhoAdjusted() {
        var calculated = calculatedEventOf(calculate(ShipperType.INDIVIDUAL, null));

        fixture.given().event(calculated)
                .when().command(new AdjustInvoiceCommand(INVOICE, new BigDecimal("-1000"),
                        "理由", null, " "))
                .then().exception(BusinessRuleViolation.class);
        fixture.given().event(calculated)
                .when().command(new AdjustInvoiceCommand(INVOICE, null,
                        "理由", null, "accountant01"))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("国内輸送では調整のあとも税を数え直す（据え置かない）")
    void recomputesTaxAfterAdjustment() {
        var domestic = new TransportRecord(
                List.of(new TransportRecord.BilledLeg(new UnLocode("JPTYO"),
                        new UnLocode("JPOSA"))),
                new BigDecimal("1000"), "GENERAL", new UnLocode("JPTYO"), new UnLocode("JPOSA"));
        var calculated = calculatedEventOf(new CalculateInvoiceCommand(INVOICE, BOOKING,
                "SHP-000001", "山田商事", ShipperType.INDIVIDUAL, DiscountRate.none(), null,
                domestic, "accountant01"));

        var captured = new InvoiceAdjustedEvent[1];
        fixture.given().event(calculated)
                .when().command(new AdjustInvoiceCommand(INVOICE, new BigDecimal("10000"),
                        "留置 4 営業日の保管料", "IMP-2026-0001", "accountant01"))
                .then().eventsSatisfy(events -> captured[0] = events.stream()
                        .map(event -> event.payload())
                        .filter(InvoiceAdjustedEvent.class::isInstance)
                        .map(InvoiceAdjustedEvent.class::cast)
                        .findFirst().orElseThrow());

        // 課税対象 50,000 + 10,000 = 60,000 → 税 6,000 → 合計 66,000
        assertThat(captured[0].taxAmount())
                .as("税を据え置くと 5,000 のままになる")
                .isEqualByComparingTo("6000");
        assertThat(captured[0].totalAmount()).isEqualByComparingTo("66000");
    }

    @Test
    @DisplayName("調整は積み上がる（2 度目は 1 度目の上に乗る）")
    void accumulatesAdjustments() {
        var calculated = calculatedEventOf(calculate(ShipperType.INDIVIDUAL, null));
        var first = new InvoiceAdjustedEvent(INVOICE, new BigDecimal("-10000"), "遅延の補償",
                "EX-1", new BigDecimal("-10000"), BigDecimal.ZERO, new BigDecimal("500000"),
                "JPY", "accountant01", NOW);

        var captured = new InvoiceAdjustedEvent[1];
        fixture.given().event(calculated).event(first)
                .when().command(new AdjustInvoiceCommand(INVOICE, new BigDecimal("-5000"),
                        "破損の補償", "EX-2", "accountant01"))
                .then().eventsSatisfy(events -> captured[0] = events.stream()
                        .map(event -> event.payload())
                        .filter(InvoiceAdjustedEvent.class::isInstance)
                        .map(InvoiceAdjustedEvent.class::cast)
                        .findFirst().orElseThrow());

        assertThat(captured[0].adjustmentTotal()).isEqualByComparingTo("-15000");
        assertThat(captured[0].totalAmount()).isEqualByComparingTo("495000");
    }

    @Test
    @DisplayName("明細のイベントは null の行一覧でも壊れない（追記専用の形を守る）")
    void lineItemsDefaultToEmpty() {
        var event = new InvoiceCalculatedEvent(INVOICE, BOOKING, "SHP-000001", null, "INDIVIDUAL",
                null, BigDecimal.ZERO, new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("1000"), "JPY", null, "accountant01", NOW);

        assertThat(event.lineItems()).isEmpty();
    }
}
