package com.example.cargotracker.tracking.infrastructure.acl;

import com.example.cargotracker.billing.application.internal.outboundservices.acl
        .TrackingStatusPort;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingActivity;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingBookingId;
import com.example.cargotracker.tracking.domain.model.valueobjects.TransportStatus;
import com.example.cargotracker.tracking.domain.repository.TrackingActivityRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link TrackingStatusPort} の実装（ACL のアダプタ。US21）。
 *
 * <p><strong>9 値の {@code TransportStatus} を 1 ビットに変換する</strong>（ADR-005）。
 * Billing が要るのは「引取まで済んだか」だけである。列挙型ごと運ぶと、
 * Tracking が状態を 1 つ増やすたびに Billing の判定を見直すことになる。
 *
 * <p><strong>追跡が始まっていない貨物を例外にしない。</strong>
 * 請求できないだけであり、異常ではない。
 */
@Component
public class TrackingStatusAdapter implements TrackingStatusPort {

    private final TrackingActivityRepository repository;

    public TrackingStatusAdapter(TrackingActivityRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean isClaimed(String bookingId) {
        if (bookingId == null || bookingId.isBlank()) {
            return false;
        }
        TrackingBookingId id;
        try {
            id = new TrackingBookingId(UUID.fromString(bookingId.strip()));
        } catch (IllegalArgumentException e) {
            return false;
        }
        return repository.findByBookingId(id)
                .map(TrackingActivity::transportStatus)
                .filter(status -> status == TransportStatus.CLAIMED)
                .isPresent();
    }
}
