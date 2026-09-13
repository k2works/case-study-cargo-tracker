package com.example.cargotracker.booking.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.domain.model.events.BookingConfirmedEvent;
import com.example.cargotracker.booking.domain.model.events.BookingDeliveredEvent;
import com.example.cargotracker.booking.domain.model.events.BookingDeliveryRevertedEvent;
import com.example.cargotracker.booking.domain.model.events.BookingSettledEvent;
import com.example.cargotracker.booking.domain.model.events.HandlingRecordedEvent;
import com.example.cargotracker.booking.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.booking.domain.model.events.CargoRoutedEvent;
import com.example.cargotracker.booking.domain.model.events.TrackingNumberIssuedEvent;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindBookingQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueryHandler;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 精算が予約の投影に出ること（US23 §受入基準 4）。
 *
 * <p><b>集約側は {@code CargoSettlementTest} が固定している。</b> そちらは
 * 「引取済からだけ精算済へ進む」ことしか見ないので、<b>投影に書き手が無くても
 * 緑になる</b>。実際 IT14 では、集約・受け入れ・クラスタ E2E の手前まで全部
 * 緑のまま、予約の一覧だけが引取済のまま残っていた。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CargoSettlementProjectionIT extends AbstractAxonIntegrationTest {

    private static final Instant AT = Instant.parse("2026-09-20T02:00:00Z");

    @Autowired
    private CargoProjection projection;

    /** 荷役・引取・精算は {@code CargoProgressProjection} が写す（同じ Processing Group）。 */
    @Autowired
    private CargoProgressProjection progress;

    @Autowired
    private BookingQueryHandler queries;

    /** 引取済の予約を作る（精算は引取済からしか進まない）。 */
    private String delivered() {
        String bookingId = "B-S-" + System.nanoTime();
        projection.on(new CargoBookedEvent(bookingId, "SHP-000001", "JPTYO", "USNYC",
                LocalDate.of(2026, Month.DECEMBER, 1), "GENERAL", new BigDecimal("1200"),
                new BigDecimal("120"), new BigDecimal("80"), new BigDecimal("100"), 10,
                "精算の貨物", null, null, null, null, "sales01"));
        projection.on(new CargoRoutedEvent(bookingId, List.of(new CargoRoutedEvent.Leg(
                "V-MOL-001", "JPTYO", "USNYC", Instant.parse("2026-09-10T00:00:00Z"),
                Instant.parse("2026-09-25T00:00:00Z"))), "routing01", AT));
        projection.on(new BookingConfirmedEvent(bookingId, "sales01", AT));
        projection.on(new TrackingNumberIssuedEvent(bookingId,
                "TRK-S" + System.nanoTime() % 1000000000L, "SHP-000001", "JPTYO", "USNYC",
                "GENERAL", new BigDecimal("1200"), List.of(), "routing01", AT));
        progress.on(new BookingDeliveredEvent(bookingId, "TRK-8K2QX7M4RB",
                Instant.parse("2026-09-25T02:00:00Z"), "USNYC"));
        return bookingId;
    }

    @Test
    @DisplayName("US23 §4: 入金のあとに予約が精算済で出る（記録と読み口は対で出す）")
    void writesSettled() {
        String bookingId = delivered();
        assertThat(queries.handle(new FindBookingQuery(bookingId)).bookingStatus())
                .isEqualTo("DELIVERED");

        progress.on(new BookingSettledEvent(bookingId, "INV-20260928-1a2b3c4d",
                new BigDecimal("510000"), "JPY", Instant.parse("2026-10-05T02:00:00Z"),
                "accountant01", Instant.parse("2026-10-05T02:30:00Z")));

        assertThat(queries.handle(new FindBookingQuery(bookingId)).bookingStatus())
                .as("集約が精算済になっても、ここに書き手が無ければ営業の一覧は引取済のまま")
                .isEqualTo("SETTLED");
    }

    @Test
    @DisplayName("二度届いても壊れない（Event Processor は at-least-once）")
    void isIdempotent() {
        String bookingId = delivered();
        var settled = new BookingSettledEvent(bookingId, "INV-20260928-1a2b3c4d",
                new BigDecimal("510000"), "JPY", Instant.parse("2026-10-05T02:00:00Z"),
                "accountant01", Instant.parse("2026-10-05T02:30:00Z"));

        progress.on(settled);
        progress.on(settled);

        assertThat(queries.handle(new FindBookingQuery(bookingId)).bookingStatus())
                .isEqualTo("SETTLED");
    }

    @Test
    @DisplayName("知らない予約に届いても落ちない（書けなかったことは警告で残す）")
    void toleratesUnknownBooking() {
        // **投影に行が無いことは起こりうる。** 投影が遅れている・弾かれた予約に
        // 後続が届く形である。ここで例外にすると Event Processor が止まり、
        // **無関係の予約のイベントまで退避される**（IT12 で実測した被害の形）。
        String unknown = "B-NONE-" + System.nanoTime();

        progress.on(new BookingDeliveredEvent(unknown, "TRK-8K2QX7M4RB",
                Instant.parse("2026-09-25T02:00:00Z"), "USNYC"));
        progress.on(new BookingSettledEvent(unknown, "INV-20260928-1a2b3c4d",
                new BigDecimal("510000"), "JPY", Instant.parse("2026-10-05T02:00:00Z"),
                "accountant01", Instant.parse("2026-10-05T02:30:00Z")));
        progress.on(new BookingDeliveryRevertedEvent(unknown, "TRK-8K2QX7M4RB",
                "IN_TRANSIT", "取り違え"));
        progress.on(new HandlingRecordedEvent(unknown, "act-1", "RECEIVE", "JPTYO",
                Instant.parse("2026-09-20T01:00:00Z"), AT));

        assertThat(queries.handle(new FindBookingQuery(unknown))).isNull();
    }
}
