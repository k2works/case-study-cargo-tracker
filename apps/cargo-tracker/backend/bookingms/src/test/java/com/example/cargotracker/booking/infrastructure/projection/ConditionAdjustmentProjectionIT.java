package com.example.cargotracker.booking.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.booking.domain.model.events.CargoRoutedEvent;
import com.example.cargotracker.booking.domain.model.events.ConditionReviewRequestedEvent;
import com.example.cargotracker.booking.domain.model.events.RouteSpecificationAdjustedEvent;
import com.example.cargotracker.booking.domain.model.events.RoutingRequestedEvent;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.BookingView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.ConditionReviewView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindBookingItineraryQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindBookingQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindConditionReviewsQuery;
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
 * 条件の調整と差し戻しの投影（US10 / ADR-0009）。
 *
 * <p>決定ごとに検査を対応させる。集約の検査（{@code CargoConditionAdjustmentTest}）は
 * 「集約が何を許すか」を見るもので、<b>投影がどう見えるか</b>は判別しない。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConditionAdjustmentProjectionIT extends AbstractAxonIntegrationTest {

    private static final LocalDate DEADLINE = LocalDate.of(2026, Month.DECEMBER, 1);
    private static final LocalDate EXTENDED = LocalDate.of(2027, Month.JANUARY, 31);
    private static final Instant AT = Instant.parse("2026-09-06T00:00:00Z");

    @Autowired
    private CargoProjection projection;

    @Autowired
    private BookingQueryHandler queries;

    private static CargoBookedEvent booked(String bookingId) {
        return new CargoBookedEvent(bookingId, "SHP-CA", "JPTYO", "USNYC", DEADLINE,
                "GENERAL", new BigDecimal("1200"), new BigDecimal("120"),
                new BigDecimal("80"), new BigDecimal("100"), 10, "自動車部品",
                null, null, null, null, "sales01");
    }

    private String handedOver() {
        String bookingId = "B-CA-" + System.nanoTime();
        projection.on(booked(bookingId));
        projection.on(new RoutingRequestedEvent(bookingId, "sales01"));
        return bookingId;
    }

    private void route(String bookingId) {
        projection.on(new CargoRoutedEvent(bookingId,
                List.of(new CargoRoutedEvent.Leg("V-MOL-001", "JPTYO", "USNYC",
                        Instant.parse("2026-09-10T00:00:00Z"),
                        Instant.parse("2026-09-24T09:00:00Z"))),
                "routing01", AT));
    }

    private BookingView booking(String bookingId) {
        return queries.handle(new FindBookingQuery(bookingId));
    }

    @Test
    @DisplayName("ADR-0009 決定 3: 条件を調整すると期限が変わり、設計し直しになる")
    void adjustmentUpdatesDeadlineAndReopensDesign() {
        String bookingId = handedOver();
        route(bookingId);
        assertThat(booking(bookingId).routingStatus()).isEqualTo("ROUTED");

        projection.on(new RouteSpecificationAdjustedEvent(bookingId, EXTENDED,
                List.of("SGSIN"), "JPOSA", "routing01", AT));

        BookingView view = booking(bookingId);
        assertThat(view.arrivalDeadline()).isEqualTo(EXTENDED);
        assertThat(view.routingStatus()).isEqualTo("ROUTING_REQUESTED");
    }

    @Test
    @DisplayName("ADR-0009 決定 3: 条件を調整しても確定済みの旅程は残る")
    void adjustmentKeepsTheItinerary() {
        // 消すと「何を組み直すのか」が分からなくなる。再設計で入れ替わるまで残す。
        String bookingId = handedOver();
        route(bookingId);

        projection.on(new RouteSpecificationAdjustedEvent(bookingId, EXTENDED,
                List.of(), null, "routing01", AT));

        assertThat(queries.handle(new FindBookingItineraryQuery(bookingId)).legs())
                .hasSize(1);
    }

    @Test
    @DisplayName("ADR-0009 決定 1: 差し戻しても経路設計の状態は動かない（記録だけが増える）")
    void conditionReviewRecordsWithoutMovingTheStatus() {
        String bookingId = handedOver();

        projection.on(new ConditionReviewRequestedEvent(bookingId,
                "期限内に着ける便がありません", "routing01", AT));

        assertThat(booking(bookingId).routingStatus())
                .as("状態を戻すと、一度も設計していない予約と混ざる")
                .isEqualTo("ROUTING_REQUESTED");
        assertThat(reviewOf(bookingId))
                .isEqualTo(new ConditionReviewView(bookingId,
                        booking(bookingId).bookingNumber(), "期限内に着ける便がありません", AT));
    }

    @Test
    @DisplayName("条件を調整すると、差し戻しの記録は消える（営業の手番が終わる）")
    void adjustmentClearsTheConditionReview() {
        // 残すと営業のダッシュボードに出たままになり、何度も同じ予約を開く。
        String bookingId = handedOver();
        projection.on(new ConditionReviewRequestedEvent(bookingId, "組めません", "routing01", AT));
        assertThat(reviewOf(bookingId)).isNotNull();

        projection.on(new RouteSpecificationAdjustedEvent(bookingId, EXTENDED,
                List.of(), null, "routing01", AT));

        assertThat(reviewOf(bookingId)).isNull();
    }

    private ConditionReviewView reviewOf(String bookingId) {
        return queries.handle(new FindConditionReviewsQuery(200)).items().stream()
                .filter(item -> item.bookingId().equals(bookingId))
                .findFirst().orElse(null);
    }
}
