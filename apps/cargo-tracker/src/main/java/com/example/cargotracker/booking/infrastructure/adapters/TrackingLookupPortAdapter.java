package com.example.cargotracker.booking.infrastructure.adapters;

import com.example.cargotracker.booking.application.internal.outboundservices.TrackingLookupPort;
import com.example.cargotracker.tracking.application.internal.queryservices.TrackingQueryService;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * tracking コンテキストの {@link TrackingQueryService} を booking コンテキストの
 * {@link TrackingLookupPort} に橋渡しするアダプター（アンチコラプションレイヤー）。
 *
 * <p>booking コンテキストは {@link TrackingQueryService} を直接 DI せず、
 * このアダプターを通じて追跡番号を参照する。
 */
@Component
public class TrackingLookupPortAdapter implements TrackingLookupPort {

    private final TrackingQueryService trackingQueryService;

    public TrackingLookupPortAdapter(TrackingQueryService trackingQueryService) {
        this.trackingQueryService = trackingQueryService;
    }

    @Override
    public Optional<String> findTrackingNumberByBookingId(UUID bookingId) {
        return trackingQueryService.findByBookingId(bookingId)
                .map(entry -> entry.getTrackingNumber().value());
    }
}
