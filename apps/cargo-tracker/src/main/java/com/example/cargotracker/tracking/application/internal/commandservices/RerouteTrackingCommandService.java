package com.example.cargotracker.tracking.application.internal.commandservices;

import com.example.cargotracker.tracking.domain.model.TrackingDestination;
import com.example.cargotracker.shared.domain.model.Location;
import com.example.cargotracker.tracking.domain.model.TrackingActivity;
import com.example.cargotracker.tracking.domain.model.TrackingBookingId;
import com.example.cargotracker.tracking.domain.repository.TrackingActivityRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 経路の変更を追跡に反映する（ADR-012。{@code CargoRoutedEvent} の購読先）。
 *
 * <p>追跡が持つ目的地・推定到着日は<strong>結果整合の写し</strong>である。
 */
@Service
public class RerouteTrackingCommandService {

    /** 反映の結果。**呼び出し側が数えるために返す**（ADR-009）。 */
    public enum Result {
        /** 反映した。 */
        UPDATED,
        /** 追跡がまだ無い（発行前の経路割り当て）。 */
        NOT_FOUND,
        /** 楽観的ロックの競合。 */
        CONFLICTED
    }

    private final TrackingActivityRepository trackingRepository;

    public RerouteTrackingCommandService(TrackingActivityRepository trackingRepository) {
        this.trackingRepository = trackingRepository;
    }

    @Transactional
    public Result reroute(UUID bookingId, String destinationUnlocode, LocalDate estimatedArrival) {
        Optional<TrackingActivity> found =
                trackingRepository.findByBookingId(new TrackingBookingId(bookingId));
        if (found.isEmpty()) {
            return Result.NOT_FOUND;
        }
        TrackingActivity tracking = found.get();
        tracking.reroute(new TrackingDestination(
                destinationUnlocode == null ? null : Location.of(destinationUnlocode),
                estimatedArrival));
        return trackingRepository.update(tracking) ? Result.UPDATED : Result.CONFLICTED;
    }
}
