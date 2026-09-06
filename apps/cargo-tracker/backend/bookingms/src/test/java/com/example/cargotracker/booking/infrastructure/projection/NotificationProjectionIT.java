package com.example.cargotracker.booking.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.booking.domain.model.events.CargoRoutedEvent;
import com.example.cargotracker.booking.domain.model.events.ReturnedToRoutingEvent;
import com.example.cargotracker.booking.domain.model.events.RoutingRequestedEvent;
import com.example.cargotracker.booking.domain.model.events.ShipperNotifiedEvent;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.BookingView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindBookingItineraryQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindBookingNotificationsQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindBookingQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.CountAwaitingNotificationQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.NotificationView;
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

/** 通知履歴と経路設計への差し戻しの投影（US12）。 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationProjectionIT extends AbstractAxonIntegrationTest {

    private static final Instant FIRST = Instant.parse("2026-09-06T00:00:00Z");
    private static final Instant SECOND = Instant.parse("2026-09-07T00:00:00Z");

    @Autowired
    private CargoProjection projection;

    @Autowired
    private BookingQueryHandler queries;

    private String routedBooking() {
        String bookingId = "B-NT-" + System.nanoTime();
        projection.on(new CargoBookedEvent(bookingId, "SHP-NT", "JPTYO", "USNYC",
                LocalDate.of(2026, Month.DECEMBER, 1), "GENERAL", new BigDecimal("1200"),
                new BigDecimal("120"), new BigDecimal("80"), new BigDecimal("100"),
                10, "自動車部品", null, null, null, null, "sales01"));
        projection.on(new RoutingRequestedEvent(bookingId, "sales01"));
        projection.on(new CargoRoutedEvent(bookingId,
                List.of(new CargoRoutedEvent.Leg("V-MOL-001", "JPTYO", "USNYC",
                        Instant.parse("2026-09-10T00:00:00Z"),
                        Instant.parse("2026-09-24T09:00:00Z"))),
                "routing01", FIRST));
        return bookingId;
    }

    private static ShipperNotifiedEvent notified(String bookingId, Instant at, String summary) {
        return new ShipperNotifiedEvent(bookingId, "shipper@example.com", summary,
                "sales01", at);
    }

    private List<NotificationView> historyOf(String bookingId) {
        return queries.handle(new FindBookingNotificationsQuery(bookingId)).items();
    }

    @Test
    @DisplayName("US12 §4: 通知すると履歴に 1 行増え、予約は通知済みになる")
    void projectsNotification() {
        String bookingId = routedBooking();

        projection.on(notified(bookingId, FIRST, "JPTYO → USNYC / 14 日 / 2026-09-24 着"));

        // **行を丸ごと比べる。** 項目ごとに積み上げると、通知に属性が増えたときに
        // 増えた分の検査が抜ける。
        assertThat(historyOf(bookingId)).containsExactly(
                new NotificationView(FIRST, "shipper@example.com",
                        "JPTYO → USNYC / 14 日 / 2026-09-24 着", "sales01"));
        assertThat(queries.handle(new FindBookingQuery(bookingId)).bookingStatus())
                .isEqualTo("ROUTE_NOTIFIED");
    }

    @Test
    @DisplayName("US12: 再通知すると行が増える（新しい通知が先に並ぶ）")
    void reNotificationAddsARow() {
        String bookingId = routedBooking();
        projection.on(notified(bookingId, FIRST, "1 回目"));

        projection.on(notified(bookingId, SECOND, "2 回目（経由港を変更）"));

        assertThat(historyOf(bookingId)).extracting(NotificationView::summary)
                .containsExactly("2 回目（経由港を変更）", "1 回目");
    }

    @Test
    @DisplayName("US12: 同じ通知を 2 度読んでも行は増えない（リプレイの冪等性）")
    void replayDoesNotDuplicate() {
        // 識別子を内容（予約 ID と通知日時）から導いているので、採番と違って
        // リプレイのたびに積み上がらない（ADR-0008 と同じ形）。
        String bookingId = routedBooking();
        ShipperNotifiedEvent event = notified(bookingId, FIRST, "1 回目");

        projection.on(event);
        projection.on(event);

        assertThat(historyOf(bookingId)).hasSize(1);
    }

    @Test
    @DisplayName("US12: 経路設計へ戻すと作業一覧に戻り、旅程は残る")
    void projectsReturnToRouting() {
        String bookingId = routedBooking();
        projection.on(notified(bookingId, FIRST, "1 回目"));

        projection.on(new ReturnedToRoutingEvent(bookingId, "荷主が経由港の変更を希望",
                "sales01", SECOND));

        BookingView view = queries.handle(new FindBookingQuery(bookingId));
        assertThat(view.bookingStatus()).isEqualTo("ROUTE_PROPOSED");
        assertThat(view.routingStatus()).isEqualTo("ROUTING_REQUESTED");
        // 消すと「何を組み直すのか」が分からなくなる。
        assertThat(queries.handle(new FindBookingItineraryQuery(bookingId)).legs()).hasSize(1);
    }

    @Test
    @DisplayName("US12: 戻しても通知履歴は消えない（何を伝えたかは残る）")
    void returnKeepsTheNotificationHistory() {
        String bookingId = routedBooking();
        projection.on(notified(bookingId, FIRST, "1 回目"));

        projection.on(new ReturnedToRoutingEvent(bookingId, "変更希望", "sales01", SECOND));

        assertThat(historyOf(bookingId)).hasSize(1);
    }

    @Test
    @DisplayName("一度も通知していない予約の履歴は空（見つかりませんにしない）")
    void unnotifiedBookingHasEmptyHistory() {
        assertThat(historyOf(routedBooking())).isEmpty();
    }

    @Test
    @DisplayName("US12: 戻した理由は経路設計者が読める（記録だけで終わらせない）")
    void returnReasonIsReadable() {
        // **記録と読み口は対で出す。** 理由の入力を必須にしておいて誰にも届かないのは、
        // 営業に無駄な入力をさせているのと同じ（IT6 レビュー 高）。
        String bookingId = routedBooking();
        projection.on(notified(bookingId, FIRST, "1 回目"));

        projection.on(new ReturnedToRoutingEvent(bookingId, "荷主が SGSIN 経由を避けたい",
                "sales01", SECOND));

        BookingView view = queries.handle(new FindBookingQuery(bookingId));
        assertThat(view.returnReason()).isEqualTo("荷主が SGSIN 経由を避けたい");
        assertThat(view.returnedToRoutingAt()).isEqualTo(SECOND);
    }

    @Test
    @DisplayName("US12: 組み直すと戻された理由は消える（読み続けさせない）")
    void returnReasonIsClearedOnRedesign() {
        String bookingId = routedBooking();
        projection.on(notified(bookingId, FIRST, "1 回目"));
        projection.on(new ReturnedToRoutingEvent(bookingId, "変更希望", "sales01", SECOND));

        projection.on(new CargoRoutedEvent(bookingId,
                List.of(new CargoRoutedEvent.Leg("V-2", "JPTYO", "USNYC",
                        Instant.parse("2026-10-01T00:00:00Z"),
                        Instant.parse("2026-10-20T00:00:00Z"))),
                "routing01", SECOND));

        BookingView view = queries.handle(new FindBookingQuery(bookingId));
        assertThat(view.returnReason()).isNull();
        assertThat(view.returnedToRoutingAt()).isNull();
    }

    @Test
    @DisplayName("US12: 戻して組み直すと、営業の「未通知」に再び出る")
    void redesignedBookingBecomesAwaitingNotificationAgain() {
        // **通知済みの印を残すと、旧経路を伝えたまま誰も気づけない**（IT6 レビュー 中）。
        // 戻した時点で印を落とし、組み直して設定済みになったら再び数える。
        String bookingId = routedBooking();
        projection.on(notified(bookingId, FIRST, "1 回目"));
        int afterNotifying = queries.handle(new CountAwaitingNotificationQuery());

        projection.on(new ReturnedToRoutingEvent(bookingId, "変更希望", "sales01", SECOND));
        projection.on(new CargoRoutedEvent(bookingId,
                List.of(new CargoRoutedEvent.Leg("V-2", "JPTYO", "USNYC",
                        Instant.parse("2026-10-01T00:00:00Z"),
                        Instant.parse("2026-10-20T00:00:00Z"))),
                "routing01", SECOND));

        assertThat(queries.handle(new CountAwaitingNotificationQuery()))
                .as("組み直した予約は、もう一度伝える必要がある")
                .isEqualTo(afterNotifying + 1);
        // 何を伝えたかは残る。
        assertThat(historyOf(bookingId)).hasSize(1);
    }

    @Test
    @DisplayName("US10: 条件を調整しても、通知済みの印は落ちる")
    void adjustmentAlsoClearsTheNotifiedMark() {
        String bookingId = routedBooking();
        projection.on(notified(bookingId, FIRST, "1 回目"));
        int afterNotifying = queries.handle(new CountAwaitingNotificationQuery());

        projection.on(new com.example.cargotracker.booking.domain.model.events
                .RouteSpecificationAdjustedEvent(bookingId,
                java.time.LocalDate.of(2027, Month.JANUARY, 31),
                List.of(), null, "routing01", SECOND));
        projection.on(new CargoRoutedEvent(bookingId,
                List.of(new CargoRoutedEvent.Leg("V-3", "JPTYO", "USNYC",
                        Instant.parse("2026-10-01T00:00:00Z"),
                        Instant.parse("2026-10-20T00:00:00Z"))),
                "routing01", SECOND));

        assertThat(queries.handle(new CountAwaitingNotificationQuery()))
                .isEqualTo(afterNotifying + 1);
    }
}
