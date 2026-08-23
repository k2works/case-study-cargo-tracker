package com.example.bookingms.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.bookingms.application.port.CargoRepository;
import com.example.bookingms.application.port.CargoSummary;
import com.example.bookingms.domain.model.BookingId;
import com.example.bookingms.domain.model.BookingStatus;
import com.example.bookingms.domain.model.Cargo;
import com.example.bookingms.domain.model.CargoSpecification;
import com.example.bookingms.domain.model.CargoStatus;
import com.example.bookingms.domain.model.CargoType;
import com.example.bookingms.domain.model.RouteSpecification;
import com.example.bookingms.domain.model.RoutingStatus;
import com.example.bookingms.domain.model.TransportStatus;
import com.example.shared.domain.model.Location;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 条件では経路が組めないことを営業へ差し戻す（US10・[ADR-020] 決定 7）。 */
@DisplayName("条件協議の差し戻し")
class RequestConsultationUseCaseTest {

    private final List<Cargo> saved = new ArrayList<>();

    private Cargo stored = cargo(RoutingStatus.ROUTING_REQUESTED);

    private static Cargo cargo(RoutingStatus routingStatus) {
        return Cargo.restore(1L, BookingId.of("BKG-2026000001"), 1L,
                new CargoStatus(BookingStatus.PRELIMINARY, TransportStatus.NOT_RECEIVED,
                        routingStatus),
                CargoSpecification.general(new BigDecimal("12000"), 20, "電子部品", null),
                RouteSpecification.restore(Location.of("JPTYO", "Tokyo"),
                        Location.of("USLAX", "Los Angeles"),
                        LocalDate.of(2030, Month.SEPTEMBER, 1),
                        LocalDate.of(2030, Month.SEPTEMBER, 20)));
    }

    private final CargoRepository cargoes = new CargoRepository() {
        @Override
        public String nextTrackingNumber() {
            throw new UnsupportedOperationException("このテストでは採番しない");
        }

        @Override
        public Cargo save(Cargo cargo) {
            saved.add(cargo);
            return cargo;
        }

        @Override
        public Optional<Cargo> findById(Long id) {
            return Optional.of(stored);
        }

        @Override
        public Optional<CargoSummary> findByBookingId(String bookingId) {
            return "BKG-2026000001".equals(bookingId)
                    ? Optional.of(new CargoSummary(stored, "丸紅商事"))
                    : Optional.empty();
        }

        @Override
        public java.util.Optional<CargoSummary> findByTrackingNumber(String trackingNumber) {
            // この検査は追跡番号から引かない。呼ばれたら、テストの前提が変わっている
            throw new UnsupportedOperationException("この検査では使わない");
        }

        @Override
        public List<CargoSummary> search(CargoType type, String keyword,
                java.util.Collection<RoutingStatus> routingStatuses,
                com.example.bookingms.domain.model.BookingStatus bookingStatus, int limit) {
            return List.of();
        }

        @Override
        public long count(CargoType type, String keyword,
                java.util.Collection<RoutingStatus> routingStatuses,
                com.example.bookingms.domain.model.BookingStatus bookingStatus) {
            return 0;
        }
    };

    private final RequestConsultationUseCase useCase = new RequestConsultationUseCase(cargoes);

    @Test
    @DisplayName("引き渡された予約を営業へ戻せる")
    void requestsConsultation() {
        Cargo returned = useCase.request("BKG-2026000001").orElseThrow();

        assertThat(returned.routingStatus()).isEqualTo(RoutingStatus.CONSULTATION_REQUESTED);
        assertThat(saved).hasSize(1);
    }

    @Test
    @DisplayName("経路が決まった予約は戻せない（差し替えるべき場面）")
    void rejectsWhenAlreadyRouted() {
        stored = cargo(RoutingStatus.ROUTED);

        assertThatThrownBy(() -> useCase.request("BKG-2026000001"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(saved).isEmpty();
    }

    @Test
    @DisplayName("予約が見つからなければ空を返す")
    void returnsEmptyForUnknownBooking() {
        assertThat(useCase.request("BKG-9999999999")).isEmpty();
    }
}
