package com.example.cargotracker.tracking.infrastructure.acl;

import com.example.cargotracker.tracking.domain.model.TrackingDestination;
import com.example.cargotracker.booking.application.internal.outboundservices.acl.TrackingPort;
import com.example.cargotracker.tracking.domain.model.TrackingActivity;
import com.example.cargotracker.tracking.domain.model.TrackingBookingId;
import com.example.cargotracker.tracking.domain.model.TrackingNumber;
import com.example.cargotracker.tracking.domain.repository.TrackingActivityRepository;
import com.example.cargotracker.tracking.infrastructure.repositories.TrackingSequence;
import com.example.cargotracker.shared.domain.model.Location;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link TrackingPort} の実装（ACL のアダプタ）。
 *
 * <p><strong>返すのは番号の文字列だけである。</strong> {@code TrackingNumber} を
 * そのまま返すと、Booking が Tracking の型を参照することになる。
 *
 * <p>採番は Booking 側のシーケンスではなく<strong>追跡側が持つ</strong>。
 * 追跡番号は追跡の識別子であり、予約の属性ではない。
 */
@Component
public class TrackingPortAdapter implements TrackingPort {

    private final TrackingActivityRepository trackingRepository;
    private final TrackingSequence sequence;
    private final Clock clock;

    public TrackingPortAdapter(
            TrackingActivityRepository trackingRepository,
            TrackingSequence sequence,
            Clock clock) {
        this.trackingRepository = trackingRepository;
        this.sequence = sequence;
        this.clock = clock;
    }

    @Override
    @Transactional
    public String issue(
            UUID bookingId, String destinationUnlocode, LocalDate estimatedArrivalDate) {
        TrackingNumber issued = TrackingNumber.issue(clock, sequence.next());
        // **目的地と推定到着日はここで受け取る**（ADR-012）。Booking へ問い合わせない
        trackingRepository.save(TrackingActivity.issue(
                issued, new TrackingBookingId(bookingId),
                new TrackingDestination(
                        destinationUnlocode == null ? null : Location.of(destinationUnlocode),
                        estimatedArrivalDate)));
        return issued.value();
    }
}
