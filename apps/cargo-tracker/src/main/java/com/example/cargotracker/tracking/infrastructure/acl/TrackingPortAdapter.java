package com.example.cargotracker.tracking.infrastructure.acl;

import com.example.cargotracker.booking.application.internal.outboundservices.acl.TrackingPort;
import com.example.cargotracker.tracking.domain.model.TrackingActivity;
import com.example.cargotracker.tracking.domain.model.TrackingBookingId;
import com.example.cargotracker.tracking.domain.model.TrackingNumber;
import com.example.cargotracker.tracking.domain.repository.TrackingActivityRepository;
import java.time.Clock;
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
    public String issue(UUID bookingId) {
        TrackingNumber issued = TrackingNumber.issue(clock, sequence.next());
        trackingRepository.save(
                TrackingActivity.issue(issued, new TrackingBookingId(bookingId)));
        return issued.value();
    }
}
