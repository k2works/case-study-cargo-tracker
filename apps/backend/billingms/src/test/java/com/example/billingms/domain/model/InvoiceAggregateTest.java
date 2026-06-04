package com.example.billingms.domain.model;

import com.example.billingms.domain.commands.ApplyDiscountCommand;
import com.example.billingms.domain.commands.CalculateInvoiceCommand;
import com.example.billingms.domain.events.DiscountAppliedEvent;
import com.example.billingms.domain.events.InvoiceCalculatedEvent;
import com.example.billingms.domain.services.CorporateDiscountPolicy;
import com.example.billingms.domain.services.FareCalculator;
import com.example.billingms.infrastructure.outboundservices.ShipperInfoAcl;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * {@link Invoice} 集約の Axon Test Fixture テスト（US21 / IT7 タスク 2.3）。
 *
 * <p>Given-When-Then 形式で集約のイベント発火を検証する。{@link FareCalculator} と
 * {@link Clock} はリソースとして注入する。</p>
 */
class InvoiceAggregateTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 8, 20, 9, 0);

    private FixtureConfiguration<Invoice> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Invoice.class);
        fixture.registerInjectableResource(new FareCalculator(RateTable.defaultTable()));
        fixture.registerInjectableResource(new CorporateDiscountPolicy());
        fixture.registerInjectableResource(stubShipperInfoAcl());
        fixture.registerInjectableResource(
                Clock.fixed(FIXED_NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        );
    }

    private ShipperInfoAcl stubShipperInfoAcl() {
        return shipperId -> new CorporateContract(
                shipperId, ShipperType.CORPORATE, new BigDecimal("0.15"));
    }

    private TransportRecord defaultTransport() {
        return new TransportRecord(
                new BigDecimal("5300"),
                new BigDecimal("1200"),
                "GENERAL",
                8,
                "JPY"
        );
    }

    @Test
    @DisplayName("US21: CalculateInvoiceCommand 受理で basicAmount 算出 + InvoiceCalculatedEvent 発火")
    void 料金算出でCALCULATEDに遷移() {
        CalculateInvoiceCommand command = new CalculateInvoiceCommand(
                "INV-001", "B-001", "S-001", defaultTransport());

        fixture.givenNoPriorActivity()
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(new InvoiceCalculatedEvent(
                        "INV-001",
                        "B-001",
                        "S-001",
                        new BigDecimal("330000"),
                        "JPY",
                        FIXED_NOW
                ));
    }

    @Test
    @DisplayName("US21: invoiceId が空文字なら IllegalArgumentException")
    void invoiceIdが空() {
        CalculateInvoiceCommand command = new CalculateInvoiceCommand(
                "  ", "B-001", "S-001", defaultTransport());

        fixture.givenNoPriorActivity()
                .when(command)
                .expectException(IllegalArgumentException.class)
                .expectExceptionMessage("invoiceId は必須です");
    }

    @Test
    @DisplayName("US21: bookingId が null なら IllegalArgumentException")
    void bookingIdがnull() {
        CalculateInvoiceCommand command = new CalculateInvoiceCommand(
                "INV-001", null, "S-001", defaultTransport());

        fixture.givenNoPriorActivity()
                .when(command)
                .expectException(IllegalArgumentException.class)
                .expectExceptionMessage("bookingId は必須です");
    }

    @Test
    @DisplayName("US21: shipperId が空文字なら IllegalArgumentException")
    void shipperIdが空() {
        CalculateInvoiceCommand command = new CalculateInvoiceCommand(
                "INV-001", "B-001", " ", defaultTransport());

        fixture.givenNoPriorActivity()
                .when(command)
                .expectException(IllegalArgumentException.class)
                .expectExceptionMessage("shipperId は必須です");
    }

    @Test
    @DisplayName("US21: transport が null なら IllegalArgumentException")
    void transportがnull() {
        CalculateInvoiceCommand command = new CalculateInvoiceCommand(
                "INV-001", "B-001", "S-001", null);

        fixture.givenNoPriorActivity()
                .when(command)
                .expectException(IllegalArgumentException.class)
                .expectExceptionMessage("transport は必須です");
    }

    @Test
    @DisplayName("US21: HAZARDOUS 貨物では 1.6 倍係数で basicAmount が算出される")
    void HAZARDOUS貨物の料金算出() {
        TransportRecord transport = new TransportRecord(
                new BigDecimal("5300"), new BigDecimal("1200"), "HAZARDOUS", 8, "JPY");
        CalculateInvoiceCommand command = new CalculateInvoiceCommand(
                "INV-002", "B-002", "S-002", transport);

        fixture.givenNoPriorActivity()
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(new InvoiceCalculatedEvent(
                        "INV-002",
                        "B-002",
                        "S-002",
                        new BigDecimal("520800"),
                        "JPY",
                        FIXED_NOW
                ));
    }

    @Test
    @DisplayName("US21: REFRIGERATED 貨物では 2.0 倍係数で basicAmount が算出される（648,000 円）")
    void REFRIGERATED貨物の料金算出() {
        TransportRecord transport = new TransportRecord(
                new BigDecimal("5300"), new BigDecimal("1200"), "REFRIGERATED", 8, "JPY");
        CalculateInvoiceCommand command = new CalculateInvoiceCommand(
                "INV-003", "B-003", "S-003", transport);

        fixture.givenNoPriorActivity()
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(new InvoiceCalculatedEvent(
                        "INV-003",
                        "B-003",
                        "S-003",
                        // 5300 × 1200 × 0.10 = 636,000 + 8 × 1500 = 648,000
                        new BigDecimal("648000"),
                        "JPY",
                        FIXED_NOW
                ));
    }

    @Test
    @DisplayName("US21: 既存集約への CalculateInvoiceCommand 再送は例外（ADR-0012 集約レベル冪等性）")
    void 既存集約への再送は失敗() {
        CalculateInvoiceCommand command = new CalculateInvoiceCommand(
                "INV-004", "B-004", "S-004", defaultTransport());

        // 既に CALCULATED 状態の集約に対して CalculateInvoiceCommand を再送する
        // → @CommandHandler コンストラクタは新規生成専用のため例外
        fixture.given(new InvoiceCalculatedEvent(
                        "INV-004", "B-004", "S-004",
                        new BigDecimal("330000"), "JPY", FIXED_NOW))
                .when(command)
                .expectException(Exception.class);
    }

    // --- IT7 Task 3.3：US22 ApplyDiscountCommand ---

    @Test
    @DisplayName("US22: CALCULATED 状態で ApplyDiscountCommand 受理 → DiscountAppliedEvent 発火")
    void US22_割引適用() {
        ApplyDiscountCommand command = new ApplyDiscountCommand("INV-D01");

        fixture.given(new InvoiceCalculatedEvent(
                        "INV-D01", "B-D01", "S-D01",
                        new BigDecimal("330000"), "JPY", FIXED_NOW))
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(new DiscountAppliedEvent(
                        "INV-D01",
                        "S-D01",
                        new BigDecimal("0.15"),
                        // 330,000 × 0.15 = 49,500（S23 UI と一致）
                        new BigDecimal("49500"),
                        // 330,000 - 49,500 = 280,500
                        new BigDecimal("280500"),
                        FIXED_NOW
                ));
    }

    @Test
    @DisplayName("US22: PENDING（イベントなし）状態では ApplyDiscountCommand は IllegalStateException")
    void US22_PENDING状態では拒否() {
        ApplyDiscountCommand command = new ApplyDiscountCommand("INV-D02");

        // event store にイベントが無い = Aggregate 未生成
        fixture.givenNoPriorActivity()
                .when(command)
                .expectException(Exception.class);
    }

    @Test
    @DisplayName("US22: INDIVIDUAL 荷主は割引額 0 で DiscountAppliedEvent 発火（totalAmount は basicAmount のまま）")
    void US22_INDIVIDUAL荷主は割引額ゼロ() {
        // INDIVIDUAL を返す ShipperInfoAcl を別途登録
        FixtureConfiguration<Invoice> individualFixture = new AggregateTestFixture<>(Invoice.class);
        individualFixture.registerInjectableResource(new FareCalculator(RateTable.defaultTable()));
        individualFixture.registerInjectableResource(new CorporateDiscountPolicy());
        individualFixture.registerInjectableResource((ShipperInfoAcl) shipperId ->
                new CorporateContract(shipperId, ShipperType.INDIVIDUAL, BigDecimal.ZERO));
        individualFixture.registerInjectableResource(
                Clock.fixed(FIXED_NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        );

        ApplyDiscountCommand command = new ApplyDiscountCommand("INV-D03");

        individualFixture.given(new InvoiceCalculatedEvent(
                        "INV-D03", "B-D03", "S-IND-001",
                        new BigDecimal("75000"), "JPY", FIXED_NOW))
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(new DiscountAppliedEvent(
                        "INV-D03",
                        "S-IND-001",
                        BigDecimal.ZERO,
                        BigDecimal.ZERO.setScale(0),
                        new BigDecimal("75000"),
                        FIXED_NOW
                ));
    }

    @Test
    @DisplayName("US22: 割引適用 2 回（割引率変更で上書き）→ 2 回目の DiscountAppliedEvent")
    void US22_割引2回適用で上書き() {
        // ShipperInfoAcl が 30% を返すように上書きした fixture
        FixtureConfiguration<Invoice> overrideFixture = new AggregateTestFixture<>(Invoice.class);
        overrideFixture.registerInjectableResource(new FareCalculator(RateTable.defaultTable()));
        overrideFixture.registerInjectableResource(new CorporateDiscountPolicy());
        overrideFixture.registerInjectableResource((ShipperInfoAcl) shipperId ->
                new CorporateContract(shipperId, ShipperType.CORPORATE, new BigDecimal("0.30")));
        overrideFixture.registerInjectableResource(
                Clock.fixed(FIXED_NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        );

        ApplyDiscountCommand command = new ApplyDiscountCommand("INV-D04");

        overrideFixture.given(
                        new InvoiceCalculatedEvent("INV-D04", "B-D04", "S-D04",
                                new BigDecimal("330000"), "JPY", FIXED_NOW),
                        new DiscountAppliedEvent("INV-D04", "S-D04",
                                new BigDecimal("0.15"),
                                new BigDecimal("49500"),
                                new BigDecimal("280500"),
                                FIXED_NOW)
                )
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(new DiscountAppliedEvent(
                        "INV-D04",
                        "S-D04",
                        new BigDecimal("0.30"),
                        // 330,000 × 0.30 = 99,000
                        new BigDecimal("99000"),
                        // 330,000 - 99,000 = 231,000
                        new BigDecimal("231000"),
                        FIXED_NOW
                ));
    }
}
