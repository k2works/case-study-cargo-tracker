package com.example.billingms.domain.model;

import com.example.billingms.domain.commands.CalculateInvoiceCommand;
import com.example.billingms.domain.events.InvoiceCalculatedEvent;
import com.example.billingms.domain.services.FareCalculator;
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
        fixture.registerInjectableResource(
                Clock.fixed(FIXED_NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        );
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
}
