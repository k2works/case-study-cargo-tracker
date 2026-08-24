package com.example.bookingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.shared.domain.model.Location;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("経路設計の依頼（US06）")
class CargoRoutingRequestTest {

    private static final RouteSpecification ROUTE = RouteSpecification.restore(
            Location.of("JPTYO", "Tokyo"), Location.of("USLAX", "Los Angeles"),
            LocalDate.of(2026, Month.SEPTEMBER, 1), LocalDate.of(2026, Month.SEPTEMBER, 20));

    private static Cargo preliminaryCargo() {
        return Cargo.book(1L, CargoSpecification.general(new BigDecimal("1000"), null, null, null),
                ROUTE);
    }

    @Test
    @DisplayName("仮受付の予約は経路設計を依頼できる")
    void requestsRouting() {
        Cargo requested = preliminaryCargo().requestRouting();

        assertThat(requested.routingStatus()).isEqualTo(RoutingStatus.ROUTING_REQUESTED);
        assertThat(requested.awaitingRouting()).isTrue();
        // 依頼は予約の状態を変えない。確定はあくまで工程 6 の作業である
        assertThat(requested.bookingStatus()).isEqualTo(BookingStatus.PRELIMINARY);
    }

    /**
     * 二重の依頼を通さない。
     *
     * <p>通すと待ち行列に同じ予約が並び、経路設計者から見ると「同じ仕事が 2 件ある」ように見える。
     */
    @Test
    @DisplayName("依頼済みの予約には再依頼できない")
    void rejectsSecondRequest() {
        Cargo requested = preliminaryCargo().requestRouting();

        assertThatThrownBy(requested::requestRouting)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("すでに経路設計を依頼");
    }

    @Test
    @DisplayName("経路が決まった予約には依頼できない")
    void rejectsAlreadyRoutedCargo() {
        Cargo routed = CargoRestoration.restore(1L, BookingId.restore("BKG-2026000001"), 1L,
                new CargoStatus(BookingStatus.PRELIMINARY, TransportStatus.NOT_RECEIVED,
                        RoutingStatus.ROUTED),
                CargoSpecification.general(new BigDecimal("1000"), null, null, null), ROUTE);

        assertThatThrownBy(routed::requestRouting)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("すでに経路が決まって");
    }
}
