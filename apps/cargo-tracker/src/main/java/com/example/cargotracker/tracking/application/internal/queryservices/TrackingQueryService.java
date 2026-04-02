package com.example.cargotracker.tracking.application.internal.queryservices;

import com.example.cargotracker.tracking.application.internal.outboundservices.BookingInfoQueryPort;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingEntry;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingEventType;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.repository.TrackingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TrackingQueryService {

    private final TrackingRepository trackingRepository;
    private final BookingInfoQueryPort bookingInfoQueryPort;

    public TrackingQueryService(TrackingRepository trackingRepository,
                                BookingInfoQueryPort bookingInfoQueryPort) {
        this.trackingRepository = trackingRepository;
        this.bookingInfoQueryPort = bookingInfoQueryPort;
    }

    public Optional<TrackingEntry> findByTrackingNumber(String trackingNumberValue) {
        return trackingRepository.findByTrackingNumber(new TrackingNumber(trackingNumberValue));
    }

    public Optional<TrackingEntry> findByBookingId(UUID bookingId) {
        return trackingRepository.findByBookingId(bookingId);
    }

    /**
     * 追跡番号で追跡情報（荷役履歴含む）を取得する。
     */
    public Optional<TrackingInfoDto> findTrackingInfo(String trackingNumberValue) {
        TrackingNumber trackingNumber = new TrackingNumber(trackingNumberValue);
        return trackingRepository.findByTrackingNumber(trackingNumber)
                .map(entry -> {
                    List<TrackingInfoDto.HandlingEventSummary> history =
                            trackingRepository.findHandlingEventsByTrackingNumber(trackingNumber)
                                    .stream()
                                    .map(r -> new TrackingInfoDto.HandlingEventSummary(
                                            r.completionTime(),
                                            r.locationCode(),
                                            r.eventType(),
                                            resolveDisplayName(r.eventType()),
                                            r.memo()
                                    ))
                                    .toList();

                    BookingInfoQueryPort.BookingSummary bookingSummary =
                            bookingInfoQueryPort.findById(entry.getBookingId()).orElse(null);

                    String originLocation = bookingSummary != null ? bookingSummary.originLocation() : "";
                    String destinationLocation = bookingSummary != null ? bookingSummary.destinationLocation() : "";
                    LocalDate estimatedArrival = bookingSummary != null ? bookingSummary.requestedDeliveryDate() : null;

                    String currentState = history.isEmpty() ? "未受取" : history.get(0).eventTypeDisplayName();
                    String currentLocation = history.isEmpty() ? originLocation : history.get(0).locationCode();

                    return new TrackingInfoDto(
                            entry.getTrackingNumber().value(),
                            entry.getBookingId(),
                            originLocation,
                            destinationLocation,
                            estimatedArrival,
                            currentState,
                            currentLocation,
                            history
                    );
                });
    }

    private String resolveDisplayName(String eventType) {
        try {
            return TrackingEventType.valueOf(eventType).getDisplayName();
        } catch (IllegalArgumentException e) {
            return eventType;
        }
    }
}
