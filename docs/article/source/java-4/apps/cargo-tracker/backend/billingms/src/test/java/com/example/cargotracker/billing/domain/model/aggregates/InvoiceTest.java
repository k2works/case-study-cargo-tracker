package com.example.cargotracker.billing.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.billing.domain.model.commands.AdjustInvoiceCommand;
import com.example.cargotracker.billing.domain.model.commands.CalculateInvoiceCommand;
import com.example.cargotracker.billing.domain.model.commands.IssueInvoiceCommand;
import com.example.cargotracker.billing.domain.model.commands.RecordPaymentCommand;
import com.example.cargotracker.billing.domain.model.commands.ReverseAdjustmentCommand;
import com.example.cargotracker.billing.domain.model.commands.VoidInvoiceCommand;
import com.example.cargotracker.billing.domain.model.commands.VoidPaymentCommand;
import com.example.cargotracker.billing.domain.model.events.InvoiceAdjustedEvent;
import com.example.cargotracker.billing.domain.model.events.InvoiceCalculatedEvent;
import com.example.cargotracker.billing.domain.model.events.InvoiceIssuedEvent;
import com.example.cargotracker.billing.domain.model.events.InvoiceVoidedEvent;
import com.example.cargotracker.billing.domain.model.valueobjects.DiscountRate;
import com.example.cargotracker.billing.domain.model.valueobjects.LineItemType;
import com.example.cargotracker.billing.domain.model.valueobjects.RateTableFixture;
import com.example.cargotracker.billing.domain.model.valueobjects.ShipperType;
import com.example.cargotracker.billing.domain.model.valueobjects.TransportRecord;
import com.example.cargotracker.billing.domain.service.DiscountPolicy;
import com.example.cargotracker.billing.domain.service.FreightChargeCalculator;
import com.example.cargotracker.shared.contract.event.PaymentRecordedEvent;
import com.example.cargotracker.shared.contract.event.PaymentVoidedEvent;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import com.example.cargotracker.shared.domain.location.UnLocode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
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
                "CT-0012", transport(), null, "accountant01");
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
                ShipperType.INDIVIDUAL, DiscountRate.none(), null, domestic, null,
                "accountant01");

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
                .when().command(new AdjustInvoiceCommand(INVOICE, "ADJ-NEW", new BigDecimal("-10000"),
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
                .when().command(new AdjustInvoiceCommand(INVOICE, "ADJ-NEW", new BigDecimal("-10000"),
                        "  ", "EX-1", "accountant01"))
                .then().exception(BusinessRuleViolation.class);
        // 0 円の調整は履歴に意味の無い行を積む。
        fixture.given().event(calculated)
                .when().command(new AdjustInvoiceCommand(INVOICE, "ADJ-NEW", BigDecimal.ZERO,
                        "動かない調整", "EX-1", "accountant01"))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("不変条件 1: 合計が負になる調整は断る")
    void refusesAdjustmentBelowZero() {
        var calculated = calculatedEventOf(calculate(ShipperType.INDIVIDUAL, null));

        fixture.given().event(calculated)
                .when().command(new AdjustInvoiceCommand(INVOICE, "ADJ-NEW", new BigDecimal("-600000"),
                        "過大な減額", null, "accountant01"))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("算出していない請求書は調整できない")
    void refusesAdjustmentBeforeCalculation() {
        fixture.given().noPriorActivity()
                .when().command(new AdjustInvoiceCommand(INVOICE, "ADJ-NEW", new BigDecimal("-1000"),
                        "理由", null, "accountant01"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("算出の材料が欠けていれば断る（誰の・どの予約か分からない請求書を作らない）")
    void refusesIncompleteInput() {
        var transport = transport();
        fixture.given().noPriorActivity()
                .when().command(new CalculateInvoiceCommand(INVOICE, "  ", "SHP-000001", "山田商事",
                        ShipperType.INDIVIDUAL, DiscountRate.none(), null, transport, null, "a01"))
                .then().exception(BusinessRuleViolation.class);
        fixture.given().noPriorActivity()
                .when().command(new CalculateInvoiceCommand(INVOICE, BOOKING, null, "山田商事",
                        ShipperType.INDIVIDUAL, DiscountRate.none(), null, transport, null, "a01"))
                .then().exception(BusinessRuleViolation.class);
        fixture.given().noPriorActivity()
                .when().command(new CalculateInvoiceCommand(INVOICE, BOOKING, "SHP-000001", null,
                        // 荷主種別が分からなければ割引を判断できない。
                        null, DiscountRate.none(), null, transport, null, "a01"))
                .then().exception(BusinessRuleViolation.class);
        fixture.given().noPriorActivity()
                .when().command(new CalculateInvoiceCommand(INVOICE, BOOKING, "SHP-000001", null,
                        ShipperType.INDIVIDUAL, DiscountRate.none(), null, null, null, "a01"))
                .then().exception(BusinessRuleViolation.class);
        fixture.given().noPriorActivity()
                .when().command(new CalculateInvoiceCommand(" ", BOOKING, "SHP-000001", null,
                        ShipperType.INDIVIDUAL, DiscountRate.none(), null, transport, null, "a01"))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("契約番号の無い法人でも割引行は出る（率だけで根拠になる）")
    void discountLineWorksWithoutContractNumber() {
        var command = new CalculateInvoiceCommand(INVOICE, BOOKING, "SHP-000001", "山田商事",
                ShipperType.CORPORATE, DiscountRate.of(new BigDecimal("0.1000")), null,
                transport(), null, "accountant01");

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
                .when().command(new AdjustInvoiceCommand(INVOICE, "ADJ-NEW", new BigDecimal("-1000"),
                        "理由", null, " "))
                .then().exception(BusinessRuleViolation.class);
        fixture.given().event(calculated)
                .when().command(new AdjustInvoiceCommand(INVOICE, "ADJ-NEW", null,
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
                domestic, null, "accountant01"));

        var captured = new InvoiceAdjustedEvent[1];
        fixture.given().event(calculated)
                .when().command(new AdjustInvoiceCommand(INVOICE, "ADJ-NEW", new BigDecimal("10000"),
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
        var first = new InvoiceAdjustedEvent(INVOICE, "ADJ-1", null, new BigDecimal("-10000"), "遅延の補償",
                "EX-1", new BigDecimal("-10000"), BigDecimal.ZERO, new BigDecimal("500000"),
                "JPY", "accountant01", NOW);

        var captured = new InvoiceAdjustedEvent[1];
        fixture.given().event(calculated).event(first)
                .when().command(new AdjustInvoiceCommand(INVOICE, "ADJ-NEW", new BigDecimal("-5000"),
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
    @DisplayName("引き継ぎ C: 調整を取り消すと、その分だけ合計が戻る（消さずに反対向きを積む）")
    void reversesAnAdjustment() {
        var calculated = calculatedEventOf(calculate(ShipperType.INDIVIDUAL, null));
        var adjusted = new InvoiceAdjustedEvent(INVOICE, "ADJ-1", null,
                new BigDecimal("-10000"), "符号を取り違えた減額", null,
                new BigDecimal("-10000"), BigDecimal.ZERO, new BigDecimal("490000"),
                "JPY", "accountant01", NOW);

        var captured = new InvoiceAdjustedEvent[1];
        fixture.given().event(calculated).event(adjusted)
                .when().command(new ReverseAdjustmentCommand(INVOICE, "ADJ-1",
                        "符号の誤り", "accountant01"))
                .then().eventsSatisfy(events -> captured[0] = events.stream()
                        .map(event -> event.payload())
                        .filter(InvoiceAdjustedEvent.class::isInstance)
                        .map(InvoiceAdjustedEvent.class::cast)
                        .findFirst().orElseThrow());

        assertThat(captured[0].reversedAdjustmentId()).isEqualTo("ADJ-1");
        assertThat(captured[0].amount())
                .as("入れた調整の反対向き")
                .isEqualByComparingTo("10000");
        assertThat(captured[0].adjustmentTotal())
                .as("取り消したぶんだけ戻る")
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("引き継ぎ C: 取り消しの識別子は 36 文字に収まる（あふれると投影だけが退避される）")
    void reversalIdentifierFitsTheColumn() {
        // **集約は受け付けるので、あふれても赤にならない。** 気づくのは投影が
        // 退避されたときで、そこまで誰も見ない（IT13 の請求書 ID と同じ形）。
        var calculated = calculatedEventOf(calculate(ShipperType.INDIVIDUAL, null));
        String adjustmentId = "ADJ-" + java.util.UUID.randomUUID().toString().replace("-", "");
        assertThat(adjustmentId).hasSize(36);

        var adjusted = new InvoiceAdjustedEvent(INVOICE, adjustmentId, null,
                new BigDecimal("-10000"), "符号を取り違えた減額", null,
                new BigDecimal("-10000"), BigDecimal.ZERO, new BigDecimal("490000"),
                "JPY", "accountant01", NOW);

        var captured = new InvoiceAdjustedEvent[1];
        fixture.given().event(calculated).event(adjusted)
                .when().command(new ReverseAdjustmentCommand(INVOICE, adjustmentId,
                        "符号の誤り", "accountant01"))
                .then().eventsSatisfy(events -> captured[0] = events.stream()
                        .map(event -> event.payload())
                        .filter(InvoiceAdjustedEvent.class::isInstance)
                        .map(InvoiceAdjustedEvent.class::cast)
                        .findFirst().orElseThrow());

        assertThat(captured[0].adjustmentId())
                .as("列は VARCHAR(36)。末尾に足すと 40 文字になる")
                .hasSizeLessThanOrEqualTo(36);
    }

    @Test
    @DisplayName("引き継ぎ C: 取り消した調整は 2 度取り消せない（入れ直したのと同じ額になる）")
    void doesNotReverseTwice() {
        var calculated = calculatedEventOf(calculate(ShipperType.INDIVIDUAL, null));
        var adjusted = new InvoiceAdjustedEvent(INVOICE, "ADJ-1", null,
                new BigDecimal("-10000"), "符号を取り違えた減額", null,
                new BigDecimal("-10000"), BigDecimal.ZERO, new BigDecimal("490000"),
                "JPY", "accountant01", NOW);
        var reversed = new InvoiceAdjustedEvent(INVOICE, "ADJ-1-REV", "ADJ-1",
                new BigDecimal("10000"), "符号の誤り", null,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("500000"),
                "JPY", "accountant01", NOW);

        fixture.given().event(calculated).event(adjusted).event(reversed)
                .when().command(new ReverseAdjustmentCommand(INVOICE, "ADJ-1",
                        "もう一度", "accountant01"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("引き継ぎ C: 識別子の無い古い調整は取り消せない（復元では断らない）")
    void cannotReverseAnAdjustmentWithoutAnIdentifier() {
        var calculated = calculatedEventOf(calculate(ShipperType.INDIVIDUAL, null));
        // IT13 までの記録には識別子が無い。**読めなくしない**——復元は通り、
        // 取り消そうとしたときに「取り消せる調整がありません」と答える。
        var legacy = new InvoiceAdjustedEvent(INVOICE, null, null,
                new BigDecimal("-10000"), "遅延の補償", "EX-1",
                new BigDecimal("-10000"), BigDecimal.ZERO, new BigDecimal("490000"),
                "JPY", "accountant01", NOW);

        fixture.given().event(calculated).event(legacy)
                .when().command(new ReverseAdjustmentCommand(INVOICE, "ADJ-1",
                        "取り消したい", "accountant01"))
                .then().exception(IllegalTransition.class);
    }

    // ---- US23 精算（発行・入金・取消・未払い） --------------------------------

    /** 発行された請求書のイベント列。**算出 → 発行**まで進めた状態。 */
    private InvoiceIssuedEvent issuedEventOf(InvoiceCalculatedEvent calculated) {
        var captured = new InvoiceIssuedEvent[1];
        fixture.given().event(calculated)
                .when().command(new IssueInvoiceCommand(INVOICE, "accountant01"))
                .then().eventsSatisfy(events -> captured[0] = events.stream()
                        .map(event -> event.payload())
                        .filter(InvoiceIssuedEvent.class::isInstance)
                        .map(InvoiceIssuedEvent.class::cast)
                        .findFirst().orElseThrow());
        return captured[0];
    }

    @Test
    @DisplayName("US23 §1: 発行すると請求番号・金額・支払期限（発行日 + 30 日）が確定する")
    void issuesTheInvoice() {
        InvoiceIssuedEvent event = issuedEventOf(
                calculatedEventOf(calculate(ShipperType.INDIVIDUAL, null)));

        assertThat(event.invoiceId()).isEqualTo(INVOICE);
        assertThat(event.bookingId())
                .as("購読側の投影が作れる分を運ぶ（投影はコマンドを読まない）")
                .isEqualTo(BOOKING);
        assertThat(event.totalAmount()).isEqualByComparingTo("510000");
        // 業務タイムゾーン（Asia/Tokyo）の 2026-09-28。UTC で判断すると前日になる。
        assertThat(event.issuedOn()).isEqualTo(LocalDate.of(2026, Month.SEPTEMBER, 28));
        assertThat(event.dueOn())
                .as("支払期限は集約が決める（不変条件 3）。画面に決めさせない")
                .isEqualTo(LocalDate.of(2026, Month.OCTOBER, 28));
    }

    @Test
    @DisplayName("US23 §1: 発行した請求書は二度発行できない")
    void doesNotIssueTwice() {
        var calculated = calculatedEventOf(calculate(ShipperType.INDIVIDUAL, null));
        var issued = issuedEventOf(calculated);

        fixture.given().event(calculated).event(issued)
                .when().command(new IssueInvoiceCommand(INVOICE, "accountant01"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("不変条件 6: 取り消した請求書は再発行しない（新規に発行する）")
    void doesNotReissueAVoidedInvoice() {
        var calculated = calculatedEventOf(calculate(ShipperType.INDIVIDUAL, null));
        var voided = new InvoiceVoidedEvent(INVOICE, BOOKING, "宛先の誤り",
                "accountant01", NOW);

        fixture.given().event(calculated).event(voided)
                .when().command(new IssueInvoiceCommand(INVOICE, "accountant01"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("US23 §4: 入金を記録すると、予約まで届く契約イベントが出る")
    void recordsThePayment() {
        var calculated = calculatedEventOf(calculate(ShipperType.INDIVIDUAL, null));
        var issued = issuedEventOf(calculated);
        Instant paidAt = Instant.parse("2026-10-05T02:00:00Z");

        var captured = new PaymentRecordedEvent[1];
        fixture.given().event(calculated).event(issued)
                .when().command(new RecordPaymentCommand(INVOICE, "PAY-1",
                        new BigDecimal("510000"), paidAt, "accountant01"))
                .then().eventsSatisfy(events -> captured[0] = events.stream()
                        .map(event -> event.payload())
                        .filter(PaymentRecordedEvent.class::isInstance)
                        .map(PaymentRecordedEvent.class::cast)
                        .findFirst().orElseThrow());

        assertThat(captured[0].bookingId())
                .as("予約を精算済にするのは bookingms。名指しできなければ連鎖が止まる")
                .isEqualTo(BOOKING);
        assertThat(captured[0].paidAt())
                .as("入金のあった時刻が業務の事実（記録した時刻ではない）")
                .isEqualTo(paidAt);
        assertThat(captured[0].paymentId())
                .as("追記系投影の行を一意にする（少なくとも 1 回配送で二度入らない）")
                .isEqualTo("PAY-1");
    }

    @Test
    @DisplayName("US23 §3: 発行していない請求書には入金を記録できない")
    void doesNotRecordPaymentBeforeIssuing() {
        fixture.given().event(calculatedEventOf(calculate(ShipperType.INDIVIDUAL, null)))
                .when().command(new RecordPaymentCommand(INVOICE, "PAY-1",
                        new BigDecimal("510000"), NOW, "accountant01"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("不変条件 5: 入金日時の無い記録は残さない")
    void requiresThePaymentInstant() {
        var calculated = calculatedEventOf(calculate(ShipperType.INDIVIDUAL, null));

        fixture.given().event(calculated).event(issuedEventOf(calculated))
                .when().command(new RecordPaymentCommand(INVOICE, "PAY-1",
                        new BigDecimal("510000"), null, "accountant01"))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("請求額と違う入金は断る（黙って入金済にすると、残りが見えなくなる）")
    void refusesAPartialPayment() {
        var calculated = calculatedEventOf(calculate(ShipperType.INDIVIDUAL, null));

        fixture.given().event(calculated).event(issuedEventOf(calculated))
                .when().command(new RecordPaymentCommand(INVOICE, "PAY-1",
                        new BigDecimal("300000"), NOW, "accountant01"))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("入金済の請求書は取り消せない（決着したものを動かさない）")
    void doesNotVoidAPaidInvoice() {
        var calculated = calculatedEventOf(calculate(ShipperType.INDIVIDUAL, null));
        var issued = issuedEventOf(calculated);
        var paid = new PaymentRecordedEvent(INVOICE, "PAY-1", BOOKING, "SHP-000001",
                new BigDecimal("510000"), "JPY", NOW, "accountant01", NOW);

        fixture.given().event(calculated).event(issued).event(paid)
                .when().command(new VoidInvoiceCommand(INVOICE, "誤って記録した",
                        "accountant01"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("誤って記録した入金を取り消すと請求済に戻る（IT15 引き継ぎ 3）")
    void voidsARecordedPayment() {
        // **請求書の取消とは別の操作である。** 請求書は正しく、入金の記録だけが
        // 誤っている——取り違え・二重記録。IT14 のマニュアル 17 章は
        // 「いまのところ運用で引き取る」と書いていた。
        var calculated = calculatedEventOf(calculate(ShipperType.INDIVIDUAL, null));
        var issued = issuedEventOf(calculated);
        var paid = new PaymentRecordedEvent(INVOICE, "PAY-1", BOOKING, "SHP-000001",
                new BigDecimal("510000"), "JPY", NOW, "accountant01", NOW);

        fixture.given().event(calculated).event(issued).event(paid)
                .when().command(new VoidPaymentCommand(INVOICE, "PAY-1",
                        "他社の入金と取り違えた", "accountant01"))
                .then().events(new PaymentVoidedEvent(INVOICE, "PAY-1", BOOKING,
                        "他社の入金と取り違えた", "accountant01", NOW));

        assertThat(restored(calculated, issued, paid,
                new PaymentVoidedEvent(INVOICE, "PAY-1", BOOKING, "取り違え",
                        "accountant01", NOW)).overdue(issued.dueOn().plusDays(1)))
                .as("**請求済に戻るので督促がまた点く。** 入金は無かったことになる")
                .isTrue();
    }

    @Test
    @DisplayName("知らない請求書の入金は取り消せない")
    void doesNotVoidAPaymentOfAnUnknownInvoice() {
        fixture.given().noPriorActivity()
                .when().command(new VoidPaymentCommand(INVOICE, "PAY-1", "取り違え",
                        "accountant01"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("入金していない請求書の入金は取り消せない")
    void doesNotVoidAPaymentThatWasNotRecorded() {
        var calculated = calculatedEventOf(calculate(ShipperType.INDIVIDUAL, null));

        fixture.given().event(calculated).event(issuedEventOf(calculated))
                .when().command(new VoidPaymentCommand(INVOICE, "PAY-1", "取り違え",
                        "accountant01"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("取り消すのは記録した入金でなければならない（別の入金 ID は断る）")
    void doesNotVoidAnotherPayment() {
        // **識別子を見ずに状態だけで通すと、どの入金を取り消したのか残らない。**
        var calculated = calculatedEventOf(calculate(ShipperType.INDIVIDUAL, null));
        var issued = issuedEventOf(calculated);
        var paid = new PaymentRecordedEvent(INVOICE, "PAY-1", BOOKING, "SHP-000001",
                new BigDecimal("510000"), "JPY", NOW, "accountant01", NOW);

        fixture.given().event(calculated).event(issued).event(paid)
                .when().command(new VoidPaymentCommand(INVOICE, "PAY-9", "取り違え",
                        "accountant01"))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("入金の取消にも理由が要る")
    void requiresAReasonToVoidAPayment() {
        var calculated = calculatedEventOf(calculate(ShipperType.INDIVIDUAL, null));
        var issued = issuedEventOf(calculated);
        var paid = new PaymentRecordedEvent(INVOICE, "PAY-1", BOOKING, "SHP-000001",
                new BigDecimal("510000"), "JPY", NOW, "accountant01", NOW);

        fixture.given().event(calculated).event(issued).event(paid)
                .when().command(new VoidPaymentCommand(INVOICE, "PAY-1", "  ",
                        "accountant01"))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("取り消したあとはもう一度入金を記録できる（記録し直せる）")
    void acceptsANewPaymentAfterVoiding() {
        var calculated = calculatedEventOf(calculate(ShipperType.INDIVIDUAL, null));
        var issued = issuedEventOf(calculated);
        var paid = new PaymentRecordedEvent(INVOICE, "PAY-1", BOOKING, "SHP-000001",
                new BigDecimal("510000"), "JPY", NOW, "accountant01", NOW);
        var voided = new PaymentVoidedEvent(INVOICE, "PAY-1", BOOKING, "取り違え",
                "accountant01", NOW);

        fixture.given().event(calculated).event(issued).event(paid).event(voided)
                .when().command(new RecordPaymentCommand(INVOICE, "PAY-2",
                        new BigDecimal("510000"), NOW, "accountant01"))
                .then().success();
    }

    @Test
    @DisplayName("取消には理由が要る（追えない記録を残さない）")
    void requiresAReasonToVoid() {
        fixture.given().event(calculatedEventOf(calculate(ShipperType.INDIVIDUAL, null)))
                .when().command(new VoidInvoiceCommand(INVOICE, "  ", "accountant01"))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("発行した請求書には調整を入れられない（額は発行の時点で確定する）")
    void doesNotAdjustAnIssuedInvoice() {
        var calculated = calculatedEventOf(calculate(ShipperType.INDIVIDUAL, null));

        fixture.given().event(calculated).event(issuedEventOf(calculated))
                .when().command(new AdjustInvoiceCommand(INVOICE, "ADJ-NEW",
                        new BigDecimal("-1000"), "遅延の補償", null, "accountant01"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("US23 §5: 支払期限の翌日から未払い（期限当日は超過ではない）")
    void overdueStartsTheDayAfterTheDueDate() {
        var calculated = calculatedEventOf(calculate(ShipperType.INDIVIDUAL, null));
        var issued = issuedEventOf(calculated);

        // **集約の述語をそのまま回す。** 判定をテスト側に書き直すと、本番の誤りを
        // 素通りさせる。復元してから overdue を呼ぶ形にする。
        Invoice invoice = restored(calculated, issued);

        assertThat(invoice.overdue(issued.dueOn().minusDays(1)))
                .as("期限前は超過ではない").isFalse();
        assertThat(invoice.overdue(issued.dueOn()))
                .as("**期限当日は超過ではない**（当日中の入金はふつうにある）")
                .isFalse();
        assertThat(invoice.overdue(issued.dueOn().plusDays(1)))
                .as("翌日から未払い").isTrue();
    }

    @Test
    @DisplayName("未発行・入金済・取消に「期限を過ぎた」は無い")
    void onlyIssuedInvoicesCanBeOverdue() {
        var calculated = calculatedEventOf(calculate(ShipperType.INDIVIDUAL, null));
        var issued = issuedEventOf(calculated);
        LocalDate wayLater = issued.dueOn().plusDays(365);

        assertThat(restored(calculated).overdue(wayLater))
                .as("発行していない請求書に期限は無い").isFalse();
        assertThat(restored(calculated, issued,
                new PaymentRecordedEvent(INVOICE, "PAY-1", BOOKING, "SHP-000001",
                        new BigDecimal("510000"), "JPY", NOW, "accountant01", NOW))
                .overdue(wayLater))
                .as("入金済は未払いではない").isFalse();
        assertThat(restored(calculated, issued,
                new InvoiceVoidedEvent(INVOICE, BOOKING, "宛先の誤り", "accountant01", NOW))
                .overdue(wayLater))
                .as("取り消した請求書は督促しない").isFalse();
    }

    /**
     * イベント列から集約を復元する。
     *
     * <p><b>本番と同じ復元経路を通す。</b> フィールドを直接組み立てると、
     * {@code @EventSourcingHandler} の書き漏らしを素通りさせる。</p>
     */
    private static Invoice restored(Object... events) {
        Invoice invoice = new Invoice();
        for (Object event : events) {
            applyTo(invoice, event);
        }
        return invoice;
    }

    private static void applyTo(Invoice invoice, Object event) {
        for (var method : Invoice.class.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(
                    org.axonframework.eventsourcing.annotation.EventSourcingHandler.class)) {
                continue;
            }
            var parameters = method.getParameterTypes();
            if (parameters.length == 1 && parameters[0].isInstance(event)) {
                method.setAccessible(true);
                try {
                    method.invoke(invoice, event);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("復元できません: " + event, e);
                }
                return;
            }
        }
        throw new IllegalStateException("復元のハンドラがありません: " + event.getClass());
    }

    @Test
    @DisplayName("明細のイベントは null の行一覧でも壊れない（追記専用の形を守る）")
    void lineItemsDefaultToEmpty() {
        var event = new InvoiceCalculatedEvent(INVOICE, BOOKING, "SHP-000001", null, "INDIVIDUAL",
                null, BigDecimal.ZERO, new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.10"), false, new BigDecimal("1000"), "JPY", null, null,
                "accountant01", NOW);

        assertThat(event.lineItems()).isEmpty();
    }

    @Test
    @DisplayName("輸出の請求書は、調整を入れても免税のまま（税率で逆算しない）")
    void keepsTheExportExemptionAfterAdjustment() {
        // **免税は業務の判断**であって「税額が 0 だったから」ではない。割り戻しで
        // 復元していたころ、税率 0% の期間には国内貨物も免税として復元された
        // （IT13 のレビュー 中）。輸出の請求書に調整を入れて、税が 0 のままで
        // あることを固定する。
        var calculated = calculatedEventOf(calculate(ShipperType.INDIVIDUAL, null));
        assertThat(calculated.taxExempt()).as("JPTYO → USNYC は輸出").isTrue();

        var captured = new InvoiceAdjustedEvent[1];
        fixture.given().event(calculated)
                .when().command(new AdjustInvoiceCommand(INVOICE, "ADJ-NEW", new BigDecimal("12000"),
                        "留置 4 営業日の保管料", "IMP-2026-0001", "accountant01"))
                .then().eventsSatisfy(events -> captured[0] = events.stream()
                        .map(event -> event.payload())
                        .filter(InvoiceAdjustedEvent.class::isInstance)
                        .map(InvoiceAdjustedEvent.class::cast)
                        .findFirst().orElseThrow());

        assertThat(captured[0].taxAmount())
                .as("免税を落とすと、調整のぶんに税が乗る")
                .isEqualByComparingTo("0");
        assertThat(captured[0].totalAmount()).isEqualByComparingTo("522000");
    }

    @Test
    @DisplayName("算出時の税率を覚える（あとで料率が変わっても請求書は変わらない）")
    void remembersTheTaxRateOfItsCalculation() {
        var calculated = calculatedEventOf(new CalculateInvoiceCommand(INVOICE, BOOKING,
                "SHP-000001", "山田商事", ShipperType.INDIVIDUAL, DiscountRate.none(), null,
                new TransportRecord(
                        List.of(new TransportRecord.BilledLeg(new UnLocode("JPTYO"),
                                new UnLocode("JPOSA"))),
                        new BigDecimal("1000"), "GENERAL", new UnLocode("JPTYO"),
                        new UnLocode("JPOSA")),
                null, "accountant01"));

        assertThat(calculated.taxRate())
                .as("イベントに載せないと、復元のたびに料率表を読むことになる")
                .isEqualByComparingTo("0.10");
        assertThat(calculated.taxExempt()).isFalse();
    }
}
