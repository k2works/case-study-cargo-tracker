package com.example.cargotracker.booking.infrastructure.acl;

import com.example.cargotracker.booking.domain.model.BookingId;
import com.example.cargotracker.booking.domain.model.Cargo;
import com.example.cargotracker.booking.domain.model.CargoItinerary;
import com.example.cargotracker.booking.domain.model.Leg;
import com.example.cargotracker.booking.domain.repository.CargoRepository;
import com.example.cargotracker.routing.application.internal.outboundservices.acl.CargoRouteAssignments;
import com.example.cargotracker.shared.domain.model.Location;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

    public CargoRouteAssignmentsAdapter(CargoRepository cargoRepository) {
        this.cargoRepository = cargoRepository;
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
        return cargoRepository.updateRouting(cargo)
                ? AssignmentResult.ASSIGNED
                : AssignmentResult.CONFLICTED;
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
