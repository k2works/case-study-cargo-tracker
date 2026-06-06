package com.example.billingms.domain.model;

import com.example.billingms.domain.commands.ApplyDiscountCommand;
import com.example.billingms.domain.commands.CalculateInvoiceCommand;
import com.example.billingms.domain.commands.IssueInvoiceCommand;
import com.example.billingms.domain.commands.MarkOverdueCommand;
import com.example.billingms.domain.commands.RecordPartialPaymentCommand;
import com.example.billingms.domain.commands.RecordPaymentCommand;
import com.example.billingms.domain.events.DiscountAppliedEvent;
import com.example.billingms.domain.events.InvoiceCalculatedEvent;
import com.example.billingms.domain.events.InvoiceIssuedEvent;
import com.example.billingms.domain.events.InvoiceOverdueEvent;
import com.example.billingms.domain.events.PartialPaymentRecordedEvent;
import com.example.billingms.domain.events.PaymentDetailRecorded;
import com.example.shared.events.PaymentRecordedEvent;
import com.example.billingms.domain.services.CorporateDiscountPolicy;
import com.example.billingms.domain.services.FareCalculator;
import com.example.billingms.domain.services.InvoiceNumberGenerator;
import com.example.billingms.domain.services.PaymentDuePolicy;
import com.example.billingms.infrastructure.outboundservices.ShipperInfoAcl;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
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
        fixture.registerInjectableResource(stubInvoiceNumberGenerator("INV-20260820-0001"));
        fixture.registerInjectableResource(new PaymentDuePolicy(
                new com.example.billingms.config.BillingProperties(
                        30,
                        java.util.Map.of(),
                        new com.example.billingms.config.BillingProperties.Overdue("0 0 9 * * *", "Asia/Tokyo"),
                        "法人割引（%d%%）",
                        null
                )));
    }

    /**
     * テスト用のスタブ {@link InvoiceNumberGenerator}。固定の番号を返す。
     * 集約テストでは Read Model アクセスを避けたいため、Mapper モックではなく
     * 直接 InvoiceNumberGenerator のスタブを注入する。
     */
    private static InvoiceNumberGenerator stubInvoiceNumberGenerator(String fixedNumber) {
        return new InvoiceNumberGenerator(null, Clock.systemUTC()) {
            @Override
            public String next() {
                return fixedNumber;
            }
        };
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

    @Test
    @DisplayName("IT8 T4.2: manualDiscountRate 指定時は ShipperInfoAcl を呼ばず手動入力値で適用（Circuit Breaker OPEN fallback）")
    void IT8_T42_手動入力割引率はACLバイパス() {
        // ShipperInfoAcl が例外を投げる fixture（呼ばれてはいけないことを検証）
        FixtureConfiguration<Invoice> bypassFixture = new AggregateTestFixture<>(Invoice.class);
        bypassFixture.registerInjectableResource(new FareCalculator(RateTable.defaultTable()));
        bypassFixture.registerInjectableResource(new CorporateDiscountPolicy());
        bypassFixture.registerInjectableResource((ShipperInfoAcl) shipperId -> {
            throw new IllegalStateException("manualDiscountRate 指定時は ACL を呼んではいけない");
        });
        bypassFixture.registerInjectableResource(
                Clock.fixed(FIXED_NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        );

        ApplyDiscountCommand command = new ApplyDiscountCommand("INV-D05", new BigDecimal("0.20"));

        bypassFixture.given(new InvoiceCalculatedEvent(
                        "INV-D05", "B-D05", "S-D05",
                        new BigDecimal("330000"), "JPY", FIXED_NOW))
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(new DiscountAppliedEvent(
                        "INV-D05",
                        "S-D05",
                        new BigDecimal("0.20"),
                        // 330,000 × 0.20 = 66,000
                        new BigDecimal("66000"),
                        // 330,000 - 66,000 = 264,000
                        new BigDecimal("264000"),
                        FIXED_NOW
                ));
    }

    // --- IT7 Task 4.1：US23 IssueInvoiceCommand ---

    @Test
    @DisplayName("US23: CALCULATED 状態で IssueInvoiceCommand 受理 → InvoiceIssuedEvent 発火")
    void US23_精算書発行() {
        IssueInvoiceCommand command = new IssueInvoiceCommand("INV-I01");

        fixture.given(new InvoiceCalculatedEvent(
                        "INV-I01", "B-I01", "S-I01",
                        new BigDecimal("330000"), "JPY", FIXED_NOW))
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(new InvoiceIssuedEvent(
                        "INV-I01",
                        "S-I01",
                        "INV-20260820-0001",
                        LocalDate.of(2026, 9, 19), // 8/20 + 30 日
                        new BigDecimal("330000"),
                        FIXED_NOW
                ));
    }

    @Test
    @DisplayName("US23: 割引適用後の CALCULATED でも IssueInvoiceCommand 受理可能")
    void US23_割引後精算書発行() {
        IssueInvoiceCommand command = new IssueInvoiceCommand("INV-I02");

        fixture.given(
                        new InvoiceCalculatedEvent("INV-I02", "B-I02", "S-I02",
                                new BigDecimal("330000"), "JPY", FIXED_NOW),
                        new DiscountAppliedEvent("INV-I02", "S-I02",
                                new BigDecimal("0.15"),
                                new BigDecimal("49500"),
                                new BigDecimal("280500"),
                                FIXED_NOW))
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(new InvoiceIssuedEvent(
                        "INV-I02",
                        "S-I02",
                        "INV-20260820-0001",
                        LocalDate.of(2026, 9, 19),
                        new BigDecimal("280500"), // 割引後 totalAmount
                        FIXED_NOW
                ));
    }

    @Test
    @DisplayName("US23: PENDING 状態（イベント無し）では IssueInvoiceCommand は例外")
    void US23_PENDING状態では発行拒否() {
        IssueInvoiceCommand command = new IssueInvoiceCommand("INV-I03");

        fixture.givenNoPriorActivity()
                .when(command)
                .expectException(Exception.class);
    }

    @Test
    @DisplayName("US23: INVOICED 状態（既発行）では IssueInvoiceCommand は IllegalStateException")
    void US23_INVOICED状態では再発行拒否() {
        IssueInvoiceCommand command = new IssueInvoiceCommand("INV-I04");

        fixture.given(
                        new InvoiceCalculatedEvent("INV-I04", "B-I04", "S-I04",
                                new BigDecimal("330000"), "JPY", FIXED_NOW),
                        new InvoiceIssuedEvent("INV-I04", "S-I04",
                                "INV-20260820-0001",
                                LocalDate.of(2026, 9, 19),
                                new BigDecimal("330000"),
                                FIXED_NOW))
                .when(command)
                .expectException(IllegalStateException.class);
    }

    // --- IT7 Task 4.1：US23 RecordPaymentCommand ---

    @Test
    @DisplayName("US23: INVOICED 状態で RecordPaymentCommand 受理 → PaymentRecordedEvent 発火")
    void US23_入金記録() {
        LocalDateTime paidAt = LocalDateTime.of(2026, 9, 15, 14, 30);
        RecordPaymentCommand command = new RecordPaymentCommand(
                "INV-P01", "PAY-001",
                new BigDecimal("330000"), "JPY", paidAt,
                "BANK_TRANSFER", null);

        fixture.given(
                        new InvoiceCalculatedEvent("INV-P01", "B-P01", "S-P01",
                                new BigDecimal("330000"), "JPY", FIXED_NOW),
                        new InvoiceIssuedEvent("INV-P01", "S-P01",
                                "INV-20260820-0001",
                                LocalDate.of(2026, 9, 19),
                                new BigDecimal("330000"),
                                FIXED_NOW))
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(
                        new PaymentRecordedEvent(
                                "INV-P01",
                                "PAY-001",
                                "B-P01",
                                "S-P01",
                                new BigDecimal("330000"),
                                "JPY",
                                paidAt,
                                FIXED_NOW
                        ),
                        // IT8 T5.1 / ADR-0019: paymentMethod 非 null のため補完 event が連続 apply される
                        new PaymentDetailRecorded(
                                "INV-P01",
                                "PAY-001",
                                "BANK_TRANSFER",
                                null
                        ));
    }

    @Test
    @DisplayName("US23: OVERDUE 状態でも RecordPaymentCommand 受理可能（遅延入金）")
    void US23_OVERDUE状態でも入金記録可能() {
        LocalDateTime paidAt = LocalDateTime.of(2026, 9, 25, 10, 0);
        RecordPaymentCommand command = new RecordPaymentCommand(
                "INV-P02", "PAY-002",
                new BigDecimal("330000"), "JPY", paidAt,
                "BANK_TRANSFER", null);

        fixture.given(
                        new InvoiceCalculatedEvent("INV-P02", "B-P02", "S-P02",
                                new BigDecimal("330000"), "JPY", FIXED_NOW),
                        new InvoiceIssuedEvent("INV-P02", "S-P02",
                                "INV-20260820-0001",
                                LocalDate.of(2026, 9, 19),
                                new BigDecimal("330000"),
                                FIXED_NOW),
                        new InvoiceOverdueEvent("INV-P02", "S-P02", FIXED_NOW))
                .when(command)
                .expectSuccessfulHandlerExecution();
    }

    @Test
    @DisplayName("IT8 T5.1 / ADR-0019: paymentMethod / externalReference 両方 null なら PaymentDetailRecorded は apply されない")
    void IT8_T51_詳細情報なしならPaymentDetailRecordedは発火しない() {
        LocalDateTime paidAt = LocalDateTime.of(2026, 9, 15, 14, 30);
        RecordPaymentCommand command = new RecordPaymentCommand(
                "INV-P-NULL", "PAY-NULL",
                new BigDecimal("330000"), "JPY", paidAt,
                null, null);

        fixture.given(
                        new InvoiceCalculatedEvent("INV-P-NULL", "B-P-NULL", "S-P-NULL",
                                new BigDecimal("330000"), "JPY", FIXED_NOW),
                        new InvoiceIssuedEvent("INV-P-NULL", "S-P-NULL",
                                "INV-20260820-0009",
                                LocalDate.of(2026, 9, 19),
                                new BigDecimal("330000"),
                                FIXED_NOW))
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(new PaymentRecordedEvent(
                        "INV-P-NULL",
                        "PAY-NULL",
                        "B-P-NULL",
                        "S-P-NULL",
                        new BigDecimal("330000"),
                        "JPY",
                        paidAt,
                        FIXED_NOW
                ));
    }

    @Test
    @DisplayName("IT8 T5.1 / ADR-0019: externalReference のみ指定でも PaymentDetailRecorded が apply される")
    void IT8_T51_externalReferenceのみでも補完event発火() {
        LocalDateTime paidAt = LocalDateTime.of(2026, 9, 15, 14, 30);
        RecordPaymentCommand command = new RecordPaymentCommand(
                "INV-P-EXT", "PAY-EXT",
                new BigDecimal("330000"), "JPY", paidAt,
                null, "TXN-2026-0001");

        fixture.given(
                        new InvoiceCalculatedEvent("INV-P-EXT", "B-P-EXT", "S-P-EXT",
                                new BigDecimal("330000"), "JPY", FIXED_NOW),
                        new InvoiceIssuedEvent("INV-P-EXT", "S-P-EXT",
                                "INV-20260820-0010",
                                LocalDate.of(2026, 9, 19),
                                new BigDecimal("330000"),
                                FIXED_NOW))
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(
                        new PaymentRecordedEvent(
                                "INV-P-EXT",
                                "PAY-EXT",
                                "B-P-EXT",
                                "S-P-EXT",
                                new BigDecimal("330000"),
                                "JPY",
                                paidAt,
                                FIXED_NOW
                        ),
                        new PaymentDetailRecorded(
                                "INV-P-EXT",
                                "PAY-EXT",
                                null,
                                "TXN-2026-0001"
                        ));
    }

    @Test
    @DisplayName("US23: CALCULATED 状態（未発行）では RecordPaymentCommand は IllegalStateException")
    void US23_CALCULATED状態では入金拒否() {
        RecordPaymentCommand command = new RecordPaymentCommand(
                "INV-P03", "PAY-003",
                new BigDecimal("330000"), "JPY", FIXED_NOW,
                "BANK_TRANSFER", null);

        fixture.given(new InvoiceCalculatedEvent(
                        "INV-P03", "B-P03", "S-P03",
                        new BigDecimal("330000"), "JPY", FIXED_NOW))
                .when(command)
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("US23: paidAmount が totalAmount と一致しないと IllegalArgumentException")
    void US23_金額不一致で拒否() {
        RecordPaymentCommand command = new RecordPaymentCommand(
                "INV-P04", "PAY-004",
                new BigDecimal("280500"), "JPY", FIXED_NOW, // 半額入金
                "BANK_TRANSFER", null);

        fixture.given(
                        new InvoiceCalculatedEvent("INV-P04", "B-P04", "S-P04",
                                new BigDecimal("330000"), "JPY", FIXED_NOW),
                        new InvoiceIssuedEvent("INV-P04", "S-P04",
                                "INV-20260820-0001",
                                LocalDate.of(2026, 9, 19),
                                new BigDecimal("330000"),
                                FIXED_NOW))
                .when(command)
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US23: currency が集約通貨と一致しないと IllegalArgumentException")
    void US23_通貨不一致で拒否() {
        RecordPaymentCommand command = new RecordPaymentCommand(
                "INV-P05", "PAY-005",
                new BigDecimal("330000"), "USD", FIXED_NOW,
                "BANK_TRANSFER", null);

        fixture.given(
                        new InvoiceCalculatedEvent("INV-P05", "B-P05", "S-P05",
                                new BigDecimal("330000"), "JPY", FIXED_NOW),
                        new InvoiceIssuedEvent("INV-P05", "S-P05",
                                "INV-20260820-0001",
                                LocalDate.of(2026, 9, 19),
                                new BigDecimal("330000"),
                                FIXED_NOW))
                .when(command)
                .expectException(IllegalArgumentException.class);
    }

    // --- IT7 Task 4.1：US23 MarkOverdueCommand ---

    @Test
    @DisplayName("US23: INVOICED 状態で MarkOverdueCommand 受理 → InvoiceOverdueEvent 発火")
    void US23_督促() {
        MarkOverdueCommand command = new MarkOverdueCommand("INV-O01");

        fixture.given(
                        new InvoiceCalculatedEvent("INV-O01", "B-O01", "S-O01",
                                new BigDecimal("330000"), "JPY", FIXED_NOW),
                        new InvoiceIssuedEvent("INV-O01", "S-O01",
                                "INV-20260820-0001",
                                LocalDate.of(2026, 9, 19),
                                new BigDecimal("330000"),
                                FIXED_NOW))
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(new InvoiceOverdueEvent("INV-O01", "S-O01", FIXED_NOW));
    }

    @Test
    @DisplayName("US23: CALCULATED 状態では MarkOverdueCommand は IllegalStateException")
    void US23_CALCULATED状態では督促拒否() {
        MarkOverdueCommand command = new MarkOverdueCommand("INV-O02");

        fixture.given(new InvoiceCalculatedEvent(
                        "INV-O02", "B-O02", "S-O02",
                        new BigDecimal("330000"), "JPY", FIXED_NOW))
                .when(command)
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("US26: INVOICED 状態で部分入金を受理して PARTIALLY_PAID 遷移")
    void US26_部分入金で残額が残る場合はPARTIALLY_PAID遷移() {
        LocalDateTime paidAt = LocalDateTime.of(2026, 9, 1, 10, 0);
        RecordPartialPaymentCommand command = new RecordPartialPaymentCommand(
                "INV-PP01", "PAY-PP-001",
                new BigDecimal("100000"), "JPY", paidAt,
                "STRIPE", "ch_test_001");

        fixture.given(
                        new InvoiceCalculatedEvent("INV-PP01", "B-PP01", "S-PP01",
                                new BigDecimal("330000"), "JPY", FIXED_NOW),
                        new InvoiceIssuedEvent("INV-PP01", "S-PP01",
                                "INV-20260820-0001",
                                LocalDate.of(2026, 9, 19),
                                new BigDecimal("330000"),
                                FIXED_NOW))
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(
                        new PartialPaymentRecordedEvent(
                                "INV-PP01",
                                "PAY-PP-001",
                                "B-PP01",
                                "S-PP01",
                                new BigDecimal("100000"),
                                "JPY",
                                paidAt,
                                new BigDecimal("100000"),
                                new BigDecimal("330000"),
                                FIXED_NOW
                        ),
                        new PaymentDetailRecorded(
                                "INV-PP01",
                                "PAY-PP-001",
                                "STRIPE",
                                "ch_test_001"
                        ));
    }

    @Test
    @DisplayName("US26: PARTIALLY_PAID 状態で残額入金を受理して PAID 遷移と shared PaymentRecordedEvent 発火")
    void US26_残額入金でPAID遷移() {
        LocalDateTime paidAt = LocalDateTime.of(2026, 9, 10, 14, 0);
        RecordPartialPaymentCommand command = new RecordPartialPaymentCommand(
                "INV-PP02", "PAY-PP-FINAL",
                new BigDecimal("230000"), "JPY", paidAt,
                "STRIPE", "ch_test_002");

        fixture.given(
                        new InvoiceCalculatedEvent("INV-PP02", "B-PP02", "S-PP02",
                                new BigDecimal("330000"), "JPY", FIXED_NOW),
                        new InvoiceIssuedEvent("INV-PP02", "S-PP02",
                                "INV-20260820-0001",
                                LocalDate.of(2026, 9, 19),
                                new BigDecimal("330000"),
                                FIXED_NOW),
                        new PartialPaymentRecordedEvent("INV-PP02", "PAY-PP-PRE",
                                "B-PP02", "S-PP02",
                                new BigDecimal("100000"), "JPY",
                                LocalDateTime.of(2026, 9, 1, 10, 0),
                                new BigDecimal("100000"),
                                new BigDecimal("330000"),
                                FIXED_NOW))
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(
                        new PaymentRecordedEvent(
                                "INV-PP02",
                                "PAY-PP-FINAL",
                                "B-PP02",
                                "S-PP02",
                                new BigDecimal("230000"),
                                "JPY",
                                paidAt,
                                FIXED_NOW
                        ),
                        new PaymentDetailRecorded(
                                "INV-PP02",
                                "PAY-PP-FINAL",
                                "STRIPE",
                                "ch_test_002"
                        ));
    }

    @Test
    @DisplayName("US26: 残額を超過する入金は IllegalArgumentException")
    void US26_残額超過は例外() {
        RecordPartialPaymentCommand command = new RecordPartialPaymentCommand(
                "INV-PP03", "PAY-PP-OVER",
                new BigDecimal("400000"), "JPY",
                LocalDateTime.of(2026, 9, 1, 10, 0),
                "STRIPE", "ch_test_over");

        fixture.given(
                        new InvoiceCalculatedEvent("INV-PP03", "B-PP03", "S-PP03",
                                new BigDecimal("330000"), "JPY", FIXED_NOW),
                        new InvoiceIssuedEvent("INV-PP03", "S-PP03",
                                "INV-20260820-0001",
                                LocalDate.of(2026, 9, 19),
                                new BigDecimal("330000"),
                                FIXED_NOW))
                .when(command)
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US26: CALCULATED 状態では部分入金拒否（INVOICED 必須）")
    void US26_CALCULATED状態では部分入金拒否() {
        RecordPartialPaymentCommand command = new RecordPartialPaymentCommand(
                "INV-PP04", "PAY-PP-BAD",
                new BigDecimal("100000"), "JPY",
                LocalDateTime.of(2026, 9, 1, 10, 0),
                "STRIPE", "ch_test_bad");

        fixture.given(new InvoiceCalculatedEvent(
                        "INV-PP04", "B-PP04", "S-PP04",
                        new BigDecimal("330000"), "JPY", FIXED_NOW))
                .when(command)
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("US26 / M4: 通貨不一致は IllegalArgumentException")
    void US26_通貨不一致は例外() {
        RecordPartialPaymentCommand command = new RecordPartialPaymentCommand(
                "INV-PP05", "PAY-PP-CUR",
                new BigDecimal("100000"), "USD",
                LocalDateTime.of(2026, 9, 1, 10, 0),
                "STRIPE", "ch_test_cur");

        fixture.given(
                        new InvoiceCalculatedEvent("INV-PP05", "B-PP05", "S-PP05",
                                new BigDecimal("330000"), "JPY", FIXED_NOW),
                        new InvoiceIssuedEvent("INV-PP05", "S-PP05",
                                "INV-20260820-0001",
                                LocalDate.of(2026, 9, 19),
                                new BigDecimal("330000"),
                                FIXED_NOW))
                .when(command)
                .expectException(IllegalArgumentException.class);
    }
}
