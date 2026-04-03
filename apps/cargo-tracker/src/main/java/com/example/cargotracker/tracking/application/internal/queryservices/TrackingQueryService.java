package com.example.cargotracker.tracking.application.internal.queryservices;

import com.example.cargotracker.exception.domain.model.aggregates.CargoIncident;
import com.example.cargotracker.exception.domain.model.repository.CargoExceptionRepository;
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
    private final CargoExceptionRepository cargoExceptionRepository;

    public TrackingQueryService(TrackingRepository trackingRepository,
                                BookingInfoQueryPort bookingInfoQueryPort,
                                CargoExceptionRepository cargoExceptionRepository) {
        this.trackingRepository = trackingRepository;
        this.bookingInfoQueryPort = bookingInfoQueryPort;
        this.cargoExceptionRepository = cargoExceptionRepository;
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
                    List<TrackingInfoDto.ExceptionEventSummary> exceptionHistory =
                            cargoExceptionRepository.findByTrackingNumber(trackingNumber.value())
                                    .stream()
                                    .map(this::toExceptionSummary)
                                    .toList();

                    BookingInfoQueryPort.BookingSummary bookingSummary =
                            bookingInfoQueryPort.findById(entry.getBookingId()).orElse(null);

                    String originLocation = bookingSummary != null ? bookingSummary.originLocation() : "";
                    String destinationLocation = bookingSummary != null ? bookingSummary.destinationLocation() : "";
                    LocalDate estimatedArrival = bookingSummary != null ? bookingSummary.requestedDeliveryDate() : null;

                    String currentState = resolveCurrentState(history, exceptionHistory);
                    String currentLocation = resolveCurrentLocation(originLocation, history, exceptionHistory);

                    return new TrackingInfoDto(
                            entry.getTrackingNumber().value(),
                            entry.getBookingId(),
                            originLocation,
                            destinationLocation,
                            estimatedArrival,
                            currentState,
                            currentLocation,
                            history,
                            exceptionHistory
                    );
                });
    }

    private String resolveDisplayName(String eventType) {
        try {
            return TrackingEventType.valueOf(eventType).getDisplayName();
        } catch (IllegalArgumentException _) {
            return eventType;
        }
    }

    private TrackingInfoDto.ExceptionEventSummary toExceptionSummary(CargoIncident incident) {
        return new TrackingInfoDto.ExceptionEventSummary(
                incident.getOccurredAt(),
                incident.getLocationCode(),
                incident.getExceptionType().name(),
                incident.getExceptionType().getDisplayName(),
                incident.getExceptionType().getBadgeClass(),
                incident.getReason(),
                incident.getResolution(),
                "通知済み"
        );
    }

    private String resolveCurrentState(List<TrackingInfoDto.HandlingEventSummary> history,
                                       List<TrackingInfoDto.ExceptionEventSummary> exceptionHistory) {
        if (!exceptionHistory.isEmpty()) {
            return "例外発生";
        }
        if (history.isEmpty()) {
            return "引取待ち";
        }
        TrackingInfoDto.HandlingEventSummary latest = history.get(0);
        if (TrackingEventType.RECEIVE.name().equals(latest.eventType())) {
            return "引取済";
        }
        return latest.eventTypeDisplayName();
    }

    private String resolveCurrentLocation(String originLocation,
                                          List<TrackingInfoDto.HandlingEventSummary> history,
                                          List<TrackingInfoDto.ExceptionEventSummary> exceptionHistory) {
        if (!exceptionHistory.isEmpty()) {
            String exceptionLocation = exceptionHistory.get(0).locationCode();
            if (exceptionLocation != null && !exceptionLocation.isBlank()) {
                return exceptionLocation;
            }
        }
        return history.isEmpty() ? originLocation : history.get(0).locationCode();
    }
}
