package com.example.cargotracker.booking.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.domain.model.commands.LinkQuotationCommand;
import com.example.cargotracker.booking.domain.model.commands.RevertSettlementCommand;
import com.example.cargotracker.booking.domain.model.commands.SettleBookingCommand;
import com.example.cargotracker.booking.domain.model.events.BookingDeliveredEvent;
import com.example.cargotracker.booking.domain.model.events.BookingSettledEvent;
import com.example.cargotracker.booking.domain.model.events.BookingSettlementRevertedEvent;
import com.example.cargotracker.booking.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.shared.contract.event.CargoQuotedEvent;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 精算の連鎖と見積の結び付け（US23 §受入基準 4・US01 §受入基準 4）。
 *
 * <p><b>入口は連鎖だけ。</b> どちらも画面から直接送る口は無い——入金を伴わない
 * 「精算済」も、予約と無関係な「見積の結び付け」も業務として存在しない。
 * それでも<b>断り方は集約が持つ</b>ので、ここで固定する。</p>
 */
class CargoSettlementTest {

    private static final LocalDate DEADLINE = LocalDate.of(2026, Month.DECEMBER, 1);
    private static final Instant PAID_AT = Instant.parse("2026-10-05T02:00:00Z");
    private static final Instant NOW = Instant.parse("2026-09-04T00:00:00Z");

    private AxonTestFixture fixture;

    @BeforeEach
    void setUp() {
        EventSourcingConfigurer configurer = EventSourcingConfigurer.create()
                .registerEntity(EventSourcedEntityModule.autodetected(String.class, Cargo.class))
                .componentRegistry(registry -> registry.registerComponent(
                        java.time.Clock.class,
                        c -> java.time.Clock.fixed(NOW, java.time.ZoneId.of("Asia/Tokyo"))));
        fixture = AxonTestFixture.with(configurer, c -> c.disableAxonServer());
    }

    private static CargoBookedEvent bookedEvent() {
        return new CargoBookedEvent("B-0001", "SHP-000001", "JPTYO", "USNYC", DEADLINE,
                "GENERAL", new BigDecimal("1200"), new BigDecimal("120"),
                new BigDecimal("80"), new BigDecimal("100"), 10, "自動車部品",
                null, null, null, null, "sales01");
    }

    private static BookingDeliveredEvent deliveredEvent() {
        return new BookingDeliveredEvent("B-0001", "TRK-8K2QX7M4RB",
                Instant.parse("2026-10-01T02:00:00Z"), "USNYC");
    }

    private static SettleBookingCommand settle() {
        return new SettleBookingCommand("B-0001", "INV-20260928-1a2b3c4d",
                new BigDecimal("433500"), "JPY", PAID_AT, "accountant01");
    }

    @Test
    @DisplayName("US23 §4: 引取済の予約は入金の記録で精算済になる")
    void settlesADeliveredBooking() {
        fixture.given().event(bookedEvent()).event(deliveredEvent())
                .when().command(settle())
                .then().success()
                .events(new BookingSettledEvent("B-0001", "INV-20260928-1a2b3c4d",
                        new BigDecimal("433500"), "JPY", PAID_AT, "accountant01", NOW));
    }

    @Test
    @DisplayName("引取が済んでいない予約は精算済にできない（請求書の取り違え）")
    void refusesToSettleBeforeDelivery() {
        fixture.given().event(bookedEvent())
                .when().command(settle())
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("知らない予約では止まらない（Event Processor を止めない）")
    void ignoresSettlementForAnUnknownBooking() {
        // 入金は billingms に記録済みである。ここで例外にすると処理が止まり、
        // 後続の予約まで届かなくなる。
        fixture.given().noPriorActivity()
                .when().command(settle())
                .then().success().noEvents();
    }

    @Test
    @DisplayName("同じ入金が 2 度届いても精算は 1 度だけ（少なくとも 1 回配送）")
    void settlesOnlyOnce() {
        fixture.given().event(bookedEvent()).event(deliveredEvent())
                .event(new BookingSettledEvent("B-0001", "INV-20260928-1a2b3c4d",
                        new BigDecimal("433500"), "JPY", PAID_AT, "accountant01", NOW))
                .when().command(settle())
                .then().success().noEvents();
    }

    @Test
    @DisplayName("US01 §4: 見積から作った予約には見積が結び付く（概算が請求へ渡る）")
    void linksTheQuotation() {
        fixture.given().event(bookedEvent())
                .when().command(new LinkQuotationCommand("B-0001",
                        "Q-0123456789abcdef0123456789abcd", new BigDecimal("510000"),
                        "JPY", "sales01"))
                .then().success()
                .events(new CargoQuotedEvent("B-0001", "Q-0123456789abcdef0123456789abcd",
                        new BigDecimal("510000"), "JPY", NOW));
    }

    @Test
    @DisplayName("見積は二度結び付かない（押し直しで履歴が積まれない）")
    void linksTheQuotationOnlyOnce() {
        fixture.given().event(bookedEvent())
                .event(new CargoQuotedEvent("B-0001", "Q-0123456789abcdef0123456789abcd",
                        new BigDecimal("510000"), "JPY", NOW))
                .when().command(new LinkQuotationCommand("B-0001", "Q-9999",
                        new BigDecimal("1"), "JPY", "sales01"))
                .then().success().noEvents();
    }

    @Test
    @DisplayName("知らない予約には結び付けない（予約の受付は先に済んでいる）")
    void ignoresLinkingForAnUnknownBooking() {
        fixture.given().noPriorActivity()
                .when().command(new LinkQuotationCommand("B-0001", "Q-1",
                        new BigDecimal("1"), "JPY", "sales01"))
                .then().success().noEvents();
    }

    @Test
    @DisplayName("精算済は終端（そこから進む先は無い）")
    void settledIsTerminal() {
        assertThat(com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus
                .SETTLED.canTransitionTo(
                        com.example.cargotracker.booking.domain.model.valueobjects
                                .BookingStatus.CANCELLED))
                .isFalse();
    }

    @Test
    @DisplayName("入金が取り消されると予約は引取済に戻る（IT15 引き継ぎ 3）")
    void revertsSettlement() {
        // **戻さないと、入金が無いのに精算が終わっている予約が残る。**
        // 記録と打ち消しは同じ経路を通す（引き渡しの取り消しと同じ形）。
        fixture.given().event(bookedEvent()).event(deliveredEvent())
                .event(new BookingSettledEvent("B-0001", "INV-20260928-1a2b3c4d",
                        new java.math.BigDecimal("510000"), "JPY", PAID_AT,
                        "accountant01", NOW))
                .when().command(new RevertSettlementCommand("B-0001",
                        "INV-20260928-1a2b3c4d", "他社の入金と取り違えた"))
                .then().events(new BookingSettlementRevertedEvent("B-0001",
                        "INV-20260928-1a2b3c4d", "他社の入金と取り違えた"));
    }

    @Test
    @DisplayName("精算済でない予約では何も起きない（二度届いても 1 度だけ）")
    void ignoresRevertWhenNotSettled() {
        fixture.given().event(bookedEvent()).event(deliveredEvent())
                .when().command(new RevertSettlementCommand("B-0001", "INV-1", "取り違え"))
                .then().noEvents();
    }

    @Test
    @DisplayName("戻したあともう一度入金があれば、また精算済になる")
    void settlesAgainAfterRevert() {
        fixture.given().event(bookedEvent()).event(deliveredEvent())
                .event(new BookingSettledEvent("B-0001", "INV-1",
                        new java.math.BigDecimal("510000"), "JPY", PAID_AT,
                        "accountant01", NOW))
                .event(new BookingSettlementRevertedEvent("B-0001", "INV-1", "取り違え"))
                .when().command(settle())
                .then().success();
    }
}
