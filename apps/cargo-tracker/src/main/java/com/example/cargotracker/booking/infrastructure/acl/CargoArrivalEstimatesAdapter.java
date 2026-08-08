package com.example.cargotracker.booking.infrastructure.acl;

import com.example.cargotracker.booking.domain.model.BookingId;
import com.example.cargotracker.booking.domain.model.Cargo;
import com.example.cargotracker.booking.domain.repository.CargoRepository;
import com.example.cargotracker.tracking.application.internal.outboundservices.acl
        .CargoArrivalEstimates;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link CargoArrivalEstimates} の実装（ACL のアダプタ）。
 *
 * <p><strong>渡すのは素の値だけである。</strong> {@code CargoItinerary} をそのまま
 * 渡すと、Tracking が Booking のドメインを参照することになる（ArchUnit ルール 4）。
 *
 * <p><strong>希望着日ではなく、確定した旅程の到着時刻を返す。</strong>
 * 希望着日（{@code arrivalDeadline}）は荷主の要望であって見込みではない。
 * 両者を取り違えると、遅れているのに「予定どおり」と表示される。
 */
@Component
public class CargoArrivalEstimatesAdapter implements CargoArrivalEstimates {

    private final CargoRepository cargoRepository;

    public CargoArrivalEstimatesAdapter(CargoRepository cargoRepository) {
        this.cargoRepository = cargoRepository;
    }

    @Override
    public Optional<CargoArrivalEstimate> findByBookingId(String bookingId) {
        return cargoRepository.findById(new BookingId(UUID.fromString(bookingId)))
                .map(CargoArrivalEstimatesAdapter::toEstimate);
    }

    private static CargoArrivalEstimate toEstimate(Cargo cargo) {
        return new CargoArrivalEstimate(
                cargo.routeSpecification().destination().unlocode(),
                // 経路が未確定なら見込みは無い。**希望着日で代替しない**
                cargo.cargoItinerary() == null ? null : cargo.cargoItinerary().arrivalTime());
    }
}
