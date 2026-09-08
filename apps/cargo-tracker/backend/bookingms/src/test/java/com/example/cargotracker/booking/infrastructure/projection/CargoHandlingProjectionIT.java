package com.example.cargotracker.booking.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.domain.model.events.BookingConfirmedEvent;
import com.example.cargotracker.booking.domain.model.events.BookingMisroutedEvent;
import com.example.cargotracker.booking.domain.model.events.BookingDeliveredEvent;
import com.example.cargotracker.booking.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.booking.domain.model.events.CargoRoutedEvent;
import com.example.cargotracker.booking.domain.model.events.HandlingRecordedEvent;
import com.example.cargotracker.booking.domain.model.events.HandlingRevertedEvent;
import com.example.cargotracker.booking.domain.model.events.TrackingNumberIssuedEvent;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.BookingView;
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
 * 荷役が予約に反映されること（US15・US28 / 不変条件 12・13）。
 *
 * <p><b>`CargoProjectionIT` から分けた。</b> 1 ファイルが 500 行を超えると、
 * 何を確かめているファイルなのかが読めなくなる（行数の基準はそのための目安）。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CargoHandlingProjectionIT extends AbstractAxonIntegrationTest {

    @Autowired
    private CargoProjection projection;

    @Autowired
    private BookingQueryHandler queries;


    /** 予約を作る（CargoProjectionIT と同じ形の最小データ）。 */
    private static CargoBookedEvent booked(String bookingId, String shipperId, String product) {
        return new CargoBookedEvent(bookingId, shipperId, "JPTYO", "USNYC",
                LocalDate.of(2026, Month.DECEMBER, 1), "GENERAL", new BigDecimal("1200"),
                new BigDecimal("120"), new BigDecimal("80"), new BigDecimal("100"), 10,
                product, null, null, null, null, "sales01");
    }

    private static CargoRoutedEvent routed(String bookingId, List<CargoRoutedEvent.Leg> legs) {
        return new CargoRoutedEvent(bookingId, legs, "routing01",
                Instant.parse("2026-09-06T00:00:00Z"));
    }

    private static CargoRoutedEvent.Leg leg(String voyage, String from, String to,
            String load, String unload) {
        return new CargoRoutedEvent.Leg(voyage, from, to, Instant.parse(load),
                Instant.parse(unload));
    }


    private static final Instant AT = Instant.parse("2026-09-20T02:00:00Z");

    /** 追跡番号を発行したところまで作る（荷役はそのあとに起きる）。 */
    private String bookedAndTracked() {
        String bookingId = "B-H-" + System.nanoTime();
        projection.on(booked(bookingId, "SHP-000001", "荷役の貨物"));
        projection.on(routed(bookingId, List.of(
                leg("V-MOL-001", "JPTYO", "USNYC",
                        "2026-09-10T00:00:00Z", "2026-09-25T00:00:00Z"))));
        projection.on(new BookingConfirmedEvent(bookingId, "sales01", AT));
        projection.on(new TrackingNumberIssuedEvent(bookingId, "TRK-H" + System.nanoTime() % 1000000000L,
                "SHP-000001", "JPTYO", "USNYC", "GENERAL", List.of(), "routing01", AT));
        return bookingId;
    }

    private BookingView booking(String bookingId) {
        return queries.handle(new FindBookingQuery(bookingId));
    }

    @Test
    @DisplayName("US15 §4: 最後の荷役が予約に出る（一覧が JOIN しないための写し）")
    void writesTheLastHandling() {
        String bookingId = bookedAndTracked();

        projection.on(new HandlingRecordedEvent(bookingId, "act-1", "RECEIVE", "JPTYO",
                Instant.parse("2026-09-20T01:00:00Z"), AT));

        var row = booking(bookingId);
        assertThat(row.bookingStatus()).as("最初の受領で輸送中になる").isEqualTo("IN_TRANSIT");
    }

    @Test
    @DisplayName("US16 §4: 引き渡しが予約の状態に出る（記録するだけでは誰にも見えない）")
    void writesDelivered() {
        // **集約が引取済になっても、投影に書き手が無ければ一覧は輸送中のまま。**
        // クラスタで実測した欠陥（IT10 T2e）。
        String bookingId = bookedAndTracked();
        projection.on(new HandlingRecordedEvent(bookingId, "act-1", "RECEIVE", "JPTYO",
                Instant.parse("2026-09-20T01:00:00Z"), AT));

        projection.on(new BookingDeliveredEvent(bookingId, "TRK-8K2QX7M4RB",
                Instant.parse("2026-09-25T02:00:00Z"), "USNYC"));

        assertThat(booking(bookingId).bookingStatus()).isEqualTo("DELIVERED");
    }

    @Test
    @DisplayName("US28: 予定外の荷役で経路設計が誤配になり、取り消しで戻る")
    void marksAndClearsMisroute() {
        String bookingId = bookedAndTracked();

        projection.on(new BookingMisroutedEvent(bookingId, "act-1", "SGSIN", AT));
        assertThat(booking(bookingId).routingStatus()).isEqualTo("MISROUTED");

        projection.on(new HandlingRevertedEvent(bookingId, "act-1", true, AT));
        assertThat(booking(bookingId).routingStatus()).isEqualTo("ROUTED");
    }

    @Test
    @DisplayName("原因でない取り消しでは誤配を消さない（誤配に気づけなくなる）")
    void keepsMisrouteForUnrelatedVoid() {
        String bookingId = bookedAndTracked();
        projection.on(new BookingMisroutedEvent(bookingId, "act-1", "SGSIN", AT));

        projection.on(new HandlingRevertedEvent(bookingId, "act-9", false, AT));

        assertThat(booking(bookingId).routingStatus()).isEqualTo("MISROUTED");
    }

    @Test
    @DisplayName("知らない予約の荷役では止まらない")
    void ignoresHandlingForUnknownBooking() {
        projection.on(new HandlingRecordedEvent("B-NONE-" + System.nanoTime(), "act-1",
                "RECEIVE", "JPTYO", Instant.parse("2026-09-20T01:00:00Z"), AT));
    }
}
