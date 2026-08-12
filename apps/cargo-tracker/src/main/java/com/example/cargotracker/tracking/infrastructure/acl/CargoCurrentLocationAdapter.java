package com.example.cargotracker.tracking.infrastructure.acl;

import com.example.cargotracker.booking.application.internal.outboundservices.acl
        .CargoCurrentLocation;
import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingNumber;
import com.example.cargotracker.tracking.domain.repository.TrackingActivityRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * {@link CargoCurrentLocation} の実装（ACL のアダプタ。US30）。
 *
 * <p><strong>いまの場所は最後の荷役の発生場所である。</strong> 追跡の記録が
 * 1 件も無い貨物は場所を持たない — <strong>その場合は空を返す</strong>
 * （例外にすると、承認の画面ごと開けなくなる）。
 */
@Component
public class CargoCurrentLocationAdapter implements CargoCurrentLocation {

    private final TrackingActivityRepository repository;

    public CargoCurrentLocationAdapter(TrackingActivityRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Location> findByTrackingNumber(String trackingNumber) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            return Optional.empty();
        }
        return repository.findByTrackingNumber(new TrackingNumber(trackingNumber.strip()))
                .map(activity -> activity.latestEvent() == null
                        ? null : activity.latestEvent().location());
    }
}
