package com.example.cargotracker.booking.infrastructure.adapters;

import com.example.cargotracker.booking.domain.model.aggregates.BookingId;
import com.example.cargotracker.booking.domain.repository.BookingRepository;
import com.example.cargotracker.routing.application.internal.outboundservices.BookingQueryPort;
import com.example.cargotracker.routing.application.internal.outboundservices.BookingSnapshot;
import com.example.cargotracker.routing.domain.model.CargoType;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * routing コンテキストの {@link BookingQueryPort} を booking コンテキストの
 * {@link BookingRepository} に橋渡しするアダプター（アンチコラプションレイヤー）。
 *
 * <p>booking.CargoType → routing.CargoType の変換はこのクラスで一元管理する。
 * requestedDeliveryDate（希望着日）を routing の requestedArrivalDate として扱う。
 */
@Component
public class BookingQueryPortAdapter implements BookingQueryPort {

    private final BookingRepository bookingRepository;

    public BookingQueryPortAdapter(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Override
    public Optional<BookingSnapshot> findById(UUID bookingId) {
        return bookingRepository.findById(new BookingId(bookingId))
            .map(booking -> {
                var transport = booking.getTransportCondition();
                var cargo = booking.getCargoSpecification();
                return new BookingSnapshot(
                    transport.originLocation(),
                    transport.destinationLocation(),
                    transport.requestedDeliveryDate(),
                    convertCargoType(cargo.cargoType()),
                    cargo.weightKg()
                );
            });
    }

    private CargoType convertCargoType(
            com.example.cargotracker.booking.domain.model.valueobjects.CargoType bookingCargoType) {
        return switch (bookingCargoType) {
            case GENERAL_CARGO -> CargoType.GENERAL;
            case DANGEROUS_GOODS -> CargoType.HAZARDOUS;
            case REFRIGERATED -> CargoType.REFRIGERATED;
        };
    }
}
