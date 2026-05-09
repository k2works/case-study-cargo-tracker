package com.example.bookingms.application.internal.commandservices;

import com.example.bookingms.domain.events.CargoAssignedForRoutingEvent;
import com.example.bookingms.domain.events.CargoRoutedEvent;
import com.example.bookingms.domain.model.aggregates.Cargo;
import com.example.bookingms.domain.model.valueobjects.BookingId;
import com.example.bookingms.domain.model.valueobjects.BookingStatus;
import com.example.bookingms.domain.model.valueobjects.CargoType;
import com.example.bookingms.domain.model.valueobjects.RouteSpecification;
import com.example.bookingms.domain.model.valueobjects.Weight;
import com.example.bookingms.domain.ports.CargoEventPublisher;
import com.example.bookingms.domain.ports.CargoRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CargoCommandServiceTest {

    @Test
    void 存在しない貨物の確定は予約ID付きで失敗する() {
        CargoCommandService service = new CargoCommandService(new StubCargoRepository(), new NoOpEventPublisher(), c -> {});

        assertThatThrownBy(() -> service.confirmBooking("MISSING123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cargo not found: MISSING123");
    }

    @Test
    void 予約をキャンセルできる() {
        Cargo cargo = cargoWithStatus("BOOKING123", BookingStatus.PRELIMINARY);
        StubCargoRepository repository = new StubCargoRepository(cargo);
        CargoCommandService service = new CargoCommandService(repository, new NoOpEventPublisher(), c -> {});

        Cargo cancelled = service.cancelBooking("BOOKING123");

        assertThat(cancelled.getBookingStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(repository.updatedCargo).isSameAs(cancelled);
    }

    @Test
    void 経路割当時にイベントを発行する() {
        Cargo cargo = cargoWithStatus("BOOKING123", BookingStatus.PRELIMINARY);
        StubCargoRepository repository = new StubCargoRepository(cargo);
        RecordingCargoEventPublisher eventPublisher = new RecordingCargoEventPublisher();
        CargoCommandService service = new CargoCommandService(repository, eventPublisher, c -> {});

        RouteCargoCommand command = new RouteCargoCommand(List.of(new RouteCargoCommand.LegData(
                "V001",
                "JPTYO",
                "CNSHA",
                LocalDateTime.of(2026, 1, 10, 10, 0),
                LocalDateTime.of(2026, 1, 12, 10, 0)
        )));

        Cargo updated = service.assignRoute("BOOKING123", command);

        assertThat(updated.getBookingStatus()).isEqualTo(BookingStatus.ROUTE_PROPOSED);
        assertThat(eventPublisher.lastEvent).isNotNull();
        assertThat(eventPublisher.lastEvent.bookingId()).isEqualTo("BOOKING123");
        assertThat(eventPublisher.lastEvent.originUnlocode()).isEqualTo("JPTYO");
        assertThat(eventPublisher.lastEvent.destinationUnlocode()).isEqualTo("CNSHA");
    }

    @Test
    void 予約確定時にCargoAssignedForRoutingEventを発行する() {
        Cargo cargo = cargoWithStatus("BOOKING456", BookingStatus.ROUTE_PROPOSED);
        StubCargoRepository repository = new StubCargoRepository(cargo);
        RecordingCargoEventPublisher eventPublisher = new RecordingCargoEventPublisher();
        CargoCommandService service = new CargoCommandService(repository, eventPublisher, c -> {});

        Cargo confirmed = service.confirmBooking("BOOKING456");

        assertThat(confirmed.getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(eventPublisher.lastAssignedEvent).isNotNull();
        assertThat(eventPublisher.lastAssignedEvent.bookingId()).isEqualTo("BOOKING456");
        assertThat(eventPublisher.lastAssignedEvent.originUnlocode()).isEqualTo("JPTYO");
        assertThat(eventPublisher.lastAssignedEvent.destinationUnlocode()).isEqualTo("CNSHA");
        assertThat(eventPublisher.lastAssignedEvent.arrivalDeadline()).isEqualTo("2026-02-01");
    }

    private static Cargo cargoWithStatus(String bookingId, BookingStatus status) {
        return new Cargo(
                1L,
                new BookingId(bookingId),
                100L,
                status,
                CargoType.GENERAL,
                new Weight(BigDecimal.valueOf(100)),
                new Cargo.RouteDetails(
                        new RouteSpecification("JPTYO", "CNSHA", LocalDate.of(2026, 2, 1)),
                        null
                )
        );
    }

    private static final class StubCargoRepository implements CargoRepository {
        private final Cargo cargo;
        private Cargo updatedCargo;

        private StubCargoRepository() {
            this.cargo = null;
        }

        private StubCargoRepository(Cargo cargo) {
            this.cargo = cargo;
        }

        @Override
        public Cargo save(Cargo cargo) {
            return cargo;
        }

        @Override
        public void update(Cargo cargo) {
            this.updatedCargo = cargo;
        }

        @Override
        public Optional<Cargo> findByBookingId(BookingId bookingId) {
            return Optional.ofNullable(cargo)
                    .filter(found -> found.getBookingId().equals(bookingId));
        }

        @Override
        public List<Cargo> findAll() {
            return cargo == null ? List.of() : List.of(cargo);
        }
    }

    private static final class NoOpEventPublisher implements CargoEventPublisher {
        @Override
        public void publishCargoRouted(CargoRoutedEvent event) {
            // no-op for testing
        }

        @Override
        public void publishCargoAssignedForRouting(CargoAssignedForRoutingEvent event) {
            // no-op for testing
        }
    }

    private static final class RecordingCargoEventPublisher implements CargoEventPublisher {
        private CargoRoutedEvent lastEvent;
        private CargoAssignedForRoutingEvent lastAssignedEvent;

        @Override
        public void publishCargoRouted(CargoRoutedEvent event) {
            this.lastEvent = event;
        }

        @Override
        public void publishCargoAssignedForRouting(CargoAssignedForRoutingEvent event) {
            this.lastAssignedEvent = event;
        }
    }
}
