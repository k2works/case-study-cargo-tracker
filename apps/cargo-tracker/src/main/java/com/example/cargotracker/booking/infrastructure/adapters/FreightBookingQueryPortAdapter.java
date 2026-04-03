package com.example.cargotracker.booking.infrastructure.adapters;

import com.example.cargotracker.billing.application.internal.outboundservices.FreightBookingQueryPort;
import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.booking.domain.repository.BookingRepository;
import com.example.cargotracker.handling.domain.model.repository.HandlingEventRepository;
import com.example.cargotracker.handling.domain.model.valueobjects.HandlingEventType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Billing コンテキストの {@link FreightBookingQueryPort} を
 * Booking コンテキストの {@link BookingRepository} に橋渡しするアダプター（ACL）。
 *
 * <p>予約が存在しない場合、またはステータスが CONFIRMED でない場合は {@link Optional#empty()} を返す。
 */
@Component
public class FreightBookingQueryPortAdapter implements FreightBookingQueryPort {

    private final BookingRepository bookingRepository;
    private final HandlingEventRepository handlingEventRepository;

    public FreightBookingQueryPortAdapter(BookingRepository bookingRepository,
                                          HandlingEventRepository handlingEventRepository) {
        this.bookingRepository = bookingRepository;
        this.handlingEventRepository = handlingEventRepository;
    }

    @Override
    public Optional<FreightBookingQueryPort.FreightBookingSummary> findCalculableBookingById(String bookingId) {
        BookingId id;
        try {
            id = new BookingId(UUID.fromString(bookingId));
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }

        return bookingRepository.findById(id)
                .filter(booking -> booking.getStatus() == BookingStatus.CONFIRMED)
                .flatMap(this::toCalculableSummary);
    }

    private Optional<FreightBookingQueryPort.FreightBookingSummary> toCalculableSummary(Booking booking) {
        var handlingEvents = handlingEventRepository.findByBookingId(booking.getId().value());
        boolean hasReceive = handlingEvents.stream()
                .anyMatch(event -> event.getEventType() == HandlingEventType.RECEIVE);
        if (!hasReceive) {
            return Optional.empty();
        }

        var cargo = booking.getCargoSpecification();
        var transport = booking.getTransportCondition();
        return Optional.of(new FreightBookingQueryPort.FreightBookingSummary(
                booking.getId().value().toString(),
                cargo.cargoType(),
                cargo.weightKg(),
                transport.originLocation(),
                transport.destinationLocation(),
                booking.getAssignedRoute() != null ? booking.getAssignedRoute().routePath() : "—",
                booking.getAssignedRoute() != null ? booking.getAssignedRoute().estimatedArrival() : null,
                handlingEvents.size(),
                estimateDistanceKm(transport.originLocation(), transport.destinationLocation())
        ));
    }

    private BigDecimal estimateDistanceKm(String originLocation, String destinationLocation) {
        if ("JPTYO".equals(originLocation) && "SGSIN".equals(destinationLocation)) {
            return new BigDecimal("5300");
        }
        if ("JPTYO".equals(originLocation) && "USNYC".equals(destinationLocation)) {
            return new BigDecimal("10800");
        }
        return new BigDecimal("1000");
    }
}
