package com.example.cargotracker.booking.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.domain.model.events.BookingConfirmedEvent;
import com.example.cargotracker.booking.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.booking.domain.model.events.TrackingNumberIssuedEvent;
import com.example.cargotracker.booking.domain.model.events.TrackingNumberRevertedEvent;
import com.example.cargotracker.booking.domain.model.events.CargoRoutedEvent;
import com.example.cargotracker.booking.domain.model.events.RoutingRequestedEvent;
import com.example.cargotracker.booking.domain.model.events.ShipperNotifiedEvent;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.BookingView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.AwaitingConfirmationView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindAwaitingConfirmationQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindBookingQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueryHandler;
import com.example.cargotracker.booking.infrastructure.query.BookingWorklistQueryHandler;
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
 * 予約確定の投影（UC11 / US13）。
 *
 * <p>集約の検査は「集約が何を許すか」を見るもので、<b>投影がどう見えるか</b>と
 * <b>営業がその予約に行き着けるか</b>は判別しない。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConfirmationProjectionIT extends AbstractAxonIntegrationTest {

    private static final LocalDate DEADLINE = LocalDate.of(2026, Month.DECEMBER, 1);
    private static final Instant AT = Instant.parse("2026-09-06T00:00:00Z");

    @Autowired
    private CargoProjection projection;

    @Autowired
    private BookingQueryHandler queries;

    @Autowired
    private BookingWorklistQueryHandler worklistQueries;

    private String notified() {
        String bookingId = "B-CF-" + System.nanoTime();
        projection.on(new CargoBookedEvent(bookingId, "SHP-CF", "JPTYO", "USNYC", DEADLINE,
                "GENERAL", new BigDecimal("1200"), new BigDecimal("120"),
                new BigDecimal("80"), new BigDecimal("100"), 10, "自動車部品",
                null, null, null, null, "sales01"));
        projection.on(new RoutingRequestedEvent(bookingId, "sales01"));
        projection.on(new CargoRoutedEvent(bookingId,
                List.of(new CargoRoutedEvent.Leg("V-MOL-001", "JPTYO", "USNYC",
                        Instant.parse("2026-09-10T00:00:00Z"),
                        Instant.parse("2026-09-24T09:00:00Z"))),
                "routing01", AT));
        projection.on(new ShipperNotifiedEvent(bookingId, "shipper@example.com",
                "JPTYO → USNYC（1 区間）", "sales01", AT));
        return bookingId;
    }

    private BookingView booking(String bookingId) {
        return queries.handle(new FindBookingQuery(bookingId));
    }

    @Test
    @DisplayName("US13 §2: 確定すると状態が「確定」になり、確定日時が残る")
    void confirmationUpdatesTheStatusAndTimestamp() {
        String bookingId = notified();

        projection.on(new BookingConfirmedEvent(bookingId, "sales01", AT));

        BookingView view = booking(bookingId);
        assertThat(view.bookingStatus()).isEqualTo("CONFIRMED");
        assertThat(view.confirmedAt())
                .as("「いつ確定したか」を残さないと、追跡番号の発行が遅れても誰も気づけない")
                .isEqualTo(AT);
    }

    @Test
    @DisplayName("US13 §3: 通知済みで未確定の予約が営業の受け皿に立つ（行で出る）")
    void awaitingConfirmationIsListed() {
        String bookingId = notified();

        // **件数でなく行。** 件数だけでは、営業はどの予約を開けばよいか分からない。
        assertThat(awaiting())
                .as("通知したまま確定を忘れた予約に、営業が気づく手立てが要る")
                .anyMatch(item -> item.bookingId().equals(bookingId)
                        && item.notifiedAt().equals(AT));

        projection.on(new BookingConfirmedEvent(bookingId, "sales01", AT));

        assertThat(awaiting())
                .as("確定した予約は営業の手番から外れる")
                .noneMatch(item -> item.bookingId().equals(bookingId));
    }

    private List<AwaitingConfirmationView> awaiting() {
        return worklistQueries.handle(new FindAwaitingConfirmationQuery(200)).items();
    }

    @Test
    @DisplayName("US14: 発行と取り消しが投影に出る（取り消すと番号が消えて確定に戻る）")
    void trackingNumberIsProjectedAndReverted() {
        String bookingId = notified();
        projection.on(new BookingConfirmedEvent(bookingId, "sales01", AT));

        projection.on(new TrackingNumberIssuedEvent(bookingId, "T-2026-000099",
                "JPTYO", "USNYC", "GENERAL", List.of(), "routing01", AT));

        BookingView issued = booking(bookingId);
        assertThat(issued.bookingStatus()).isEqualTo("TRACKING_ISSUED");
        assertThat(issued.trackingNumber()).isEqualTo("T-2026-000099");
        assertThat(issued.trackingIssuedAt()).isEqualTo(AT);

        // 補償（ADR-0010 決定 4）。**キャンセルではない**——確定に戻り、
        // 経路設計者がもう一度発行できる。
        projection.on(new TrackingNumberRevertedEvent(bookingId, "T-2026-000099",
                "届きませんでした", AT));

        BookingView reverted = booking(bookingId);
        assertThat(reverted.bookingStatus()).isEqualTo("CONFIRMED");
        assertThat(reverted.trackingNumber()).isNull();
    }

    @Test
    @DisplayName("投影に無い予約への発行・取り消しは 500 にせず要確認一覧へ回す")
    void unknownBookingGoesToAttentionList() {
        String unknown = "B-NONE-" + System.nanoTime();

        projection.on(new TrackingNumberIssuedEvent(unknown, "T-X", "JPTYO", "USNYC",
                "GENERAL", List.of(), "routing01", AT));
        projection.on(new TrackingNumberRevertedEvent(unknown, "T-X", "届かず", AT));

        assertThat(booking(unknown))
                .as("投影が無いだけで投影全体を止めない（要確認一覧で気づく）")
                .isNull();
    }

    @Test
    @DisplayName("US13: 確定を書ける予約が投影に無くても投影を止めない")
    void unknownBookingConfirmationIsRecorded() {
        String unknown = "B-NONE-" + System.nanoTime();

        projection.on(new BookingConfirmedEvent(unknown, "sales01", AT));

        assertThat(booking(unknown)).isNull();
    }
}
