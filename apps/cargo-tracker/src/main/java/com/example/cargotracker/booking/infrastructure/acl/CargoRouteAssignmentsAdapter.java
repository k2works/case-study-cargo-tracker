package com.example.cargotracker.booking.infrastructure.acl;

import com.example.cargotracker.booking.domain.model.aggregates.BookingId;
import com.example.cargotracker.booking.domain.model.aggregates.Cargo;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoItinerary;
import com.example.cargotracker.booking.domain.model.entities.Leg;
import com.example.cargotracker.booking.domain.repository.CargoRepository;
import com.example.cargotracker.routing.application.internal.outboundservices.acl.CargoRouteAssignments;
import com.example.cargotracker.shared.domain.event.CargoRoutedEvent;
import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link CargoRouteAssignments} の実装（ACL のアダプタ）。
 *
 * <p><strong>判断はドメインが行う。</strong> ここでするのは、素の値を Booking の
 * ことばへ翻訳し、集約に頼み、結果を返すことだけである。
 */
@Component
public class CargoRouteAssignmentsAdapter implements CargoRouteAssignments {

    private final CargoRepository cargoRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public CargoRouteAssignmentsAdapter(
            CargoRepository cargoRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.cargoRepository = cargoRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AssignmentResult assign(UUID bookingId, List<LegAssignment> legs) {
        Optional<Cargo> found = cargoRepository.findById(new BookingId(bookingId));
        if (found.isEmpty()) {
            return AssignmentResult.NOT_FOUND;
        }
        Cargo cargo = found.get();
        try {
            cargo.assignItinerary(toItinerary(legs));
        } catch (IllegalStateException | IllegalArgumentException e) {
            // 割り当てられない状態・端点の食い違いは業務のエラーである。**500 にしない**
            return AssignmentResult.REJECTED;
        }
        if (!cargoRepository.updateRouting(cargo)) {
            return AssignmentResult.CONFLICTED;
        }

        // **経路が変わったことを伝える**（ADR-012）。追跡は目的地と推定到着日の写しを
        // 持っており、発行時に受け取ったきりだと経路変更に追随しない。
        // **片方だけ入れると古い到着予定が残り続ける。**
        eventPublisher.publishEvent(new CargoRoutedEvent(
                bookingId,
                cargo.routeSpecification().destination().unlocode(),
                cargo.cargoItinerary().arrivalTime().atZone(clock.getZone()).toLocalDate()));
        return AssignmentResult.ASSIGNED;
    }

    private static CargoItinerary toItinerary(List<LegAssignment> legs) {
        return CargoItinerary.of(legs.stream()
                .map(leg -> Leg.of(
                        leg.voyageNumber(),
                        Location.of(leg.loadLocationUnlocode()),
                        Location.of(leg.unloadLocationUnlocode()),
                        leg.loadTime(),
                        leg.unloadTime()))
                .toList());
    }
}
