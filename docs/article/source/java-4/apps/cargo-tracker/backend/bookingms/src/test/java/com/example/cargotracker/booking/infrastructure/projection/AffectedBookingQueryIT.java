package com.example.cargotracker.booking.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.booking.domain.model.events.CargoRoutedEvent;
import com.example.cargotracker.booking.domain.model.events.RoutingRequestedEvent;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.AffectedBookingView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindBookingsByVoyageQuery;
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
 * 航海を止める前の影響範囲（S34 / US24）。
 *
 * <p>{@code CargoProjectionIT} から切り出した。予約の投影そのものと、「その航海を
 * 誰が使っているか」を引くことは別の関心で、1 つのクラスに積むと何を確かめて
 * いるのか読めなくなる（{@code CargoRoutingTest} と同じ理由）。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AffectedBookingQueryIT extends AbstractAxonIntegrationTest {

    @Autowired
    private CargoProjection projection;

    @Autowired
    private BookingQueryHandler queries;

    private static final Instant ROUTED_AT = Instant.parse("2026-09-06T00:00:00Z");

    private static CargoBookedEvent booked(String bookingId) {
        return new CargoBookedEvent(bookingId, "SHP-AF", "JPTYO", "USNYC",
                LocalDate.of(2026, Month.DECEMBER, 1), "GENERAL", new BigDecimal("1200"),
                new BigDecimal("120"), new BigDecimal("80"), new BigDecimal("100"),
                10, "自動車部品", null, null, null, null, "sales01");
    }

    private static CargoRoutedEvent.Leg leg(String voyage, String from, String to,
            String load, String unload) {
        return new CargoRoutedEvent.Leg(voyage, from, to,
                Instant.parse(load), Instant.parse(unload));
    }

    private void route(String bookingId, List<CargoRoutedEvent.Leg> legs) {
        projection.on(new CargoRoutedEvent(bookingId, legs, "routing01", ROUTED_AT));
    }

    @Test
    @DisplayName("S34: その航海で経路を組んだ予約を引ける（US24）")
    void findsBookingsByVoyage() {
        String voyage = "V-AF-" + System.nanoTime();
        String first = "B-AF1-" + System.nanoTime();
        String second = "B-AF2-" + System.nanoTime();
        String other = "B-AF3-" + System.nanoTime();
        for (String id : List.of(first, second, other)) {
            projection.on(booked(id));
            projection.on(new RoutingRequestedEvent(id, "sales01"));
        }
        route(first, List.of(
                leg(voyage, "JPTYO", "USNYC", "2026-09-10T00:00:00Z", "2026-09-25T00:00:00Z")));
        // **同じ航海を 2 区間で使う旅程。** 区間ごとに返すと、この予約が 2 度
        // 数えられて件数が実際より多くなる。
        route(second, List.of(
                leg(voyage, "JPTYO", "SGSIN", "2026-09-10T00:00:00Z", "2026-09-16T00:00:00Z"),
                leg(voyage, "SGSIN", "USNYC", "2026-09-17T00:00:00Z", "2026-09-25T00:00:00Z")));
        // 別の航海で組んだ予約は巻き込まない。
        route(other, List.of(
                leg("V-OTHER", "JPTYO", "USNYC",
                        "2026-09-10T00:00:00Z", "2026-09-25T00:00:00Z")));

        List<AffectedBookingView> affected =
                queries.handle(new FindBookingsByVoyageQuery(voyage)).items();

        assertThat(affected).extracting(AffectedBookingView::bookingId)
                .containsExactlyInAnyOrder(first, second);
        // 状態が読めないと、止めてよいかの判断に使えない。
        assertThat(affected).allSatisfy(item -> {
            assertThat(item.bookingNumber()).isNotBlank();
            assertThat(item.bookingStatus()).isEqualTo("ROUTE_PROPOSED");
            assertThat(item.routingStatus()).isEqualTo("ROUTED");
        });
    }

    @Test
    @DisplayName("S34: 経路を組んでいない航海は空で返る（見つかりませんにしない）")
    void findsNoBookingsForUnusedVoyage() {
        assertThat(queries.handle(new FindBookingsByVoyageQuery("V-NONE-" + System.nanoTime()))
                .items()).isEmpty();
    }
}
