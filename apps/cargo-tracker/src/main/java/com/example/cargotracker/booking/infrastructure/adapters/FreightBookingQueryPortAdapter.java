package com.example.cargotracker.booking.infrastructure.adapters;

import com.example.cargotracker.billing.application.internal.outboundservices.FreightBookingQueryPort;
import com.example.cargotracker.billing.application.internal.outboundservices.FreightBookingQueryPort.FreightBookingSummary;
import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.booking.domain.repository.BookingRepository;
import org.springframework.stereotype.Component;

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

    public FreightBookingQueryPortAdapter(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Override
    public Optional<FreightBookingSummary> findConfirmedBookingById(String bookingId) {
        BookingId id;
        try {
            id = new BookingId(UUID.fromString(bookingId));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        return bookingRepository.findById(id)
                .filter(booking -> booking.getStatus() == BookingStatus.CONFIRMED)
                .map(this::toSummary);
    }

    private FreightBookingSummary toSummary(Booking booking) {
        var cargo = booking.getCargoSpecification();
        var transport = booking.getTransportCondition();
        return new FreightBookingSummary(
                booking.getId().value().toString(),
                cargo.cargoType(),
                cargo.weightKg(),
                transport.originLocation(),
                transport.destinationLocation()
        );
    }
}
