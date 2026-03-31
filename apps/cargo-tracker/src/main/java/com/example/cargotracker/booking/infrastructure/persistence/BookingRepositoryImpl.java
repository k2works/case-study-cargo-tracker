package com.example.cargotracker.booking.infrastructure.persistence;

import com.example.cargotracker.booking.domain.*;
import com.example.cargotracker.shipper.domain.ShipperId;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class BookingRepositoryImpl implements BookingRepository {

    private final BookingMapper bookingMapper;

    public BookingRepositoryImpl(BookingMapper bookingMapper) {
        this.bookingMapper = bookingMapper;
    }

    @Override
    public void save(Booking booking) {
        CargoSpecification cargo = booking.getCargoSpecification();
        TransportCondition transport = booking.getTransportCondition();

        BookingRecord record = new BookingRecord(
                booking.getId().value(),
                booking.getShipperId().value(),
                cargo.cargoType().name(),
                cargo.weightKg(),
                cargo.lengthCm(),
                cargo.widthCm(),
                cargo.heightCm(),
                cargo.quantity(),
                cargo.description(),
                transport.originLocation(),
                transport.destinationLocation(),
                transport.requestedPickupDate(),
                transport.requestedDeliveryDate(),
                booking.getStatus().name(),
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        bookingMapper.insert(record);
    }

    @Override
    public Optional<Booking> findById(BookingId id) {
        return bookingMapper.findById(id.value())
                .map(this::toBooking);
    }

    private Booking toBooking(BookingRecord record) {
        BookingId id = new BookingId(record.id());
        ShipperId shipperId = new ShipperId(record.shipperId());
        CargoSpecification cargo = new CargoSpecification(
                CargoType.valueOf(record.cargoType()),
                record.cargoWeightKg(),
                record.cargoLengthCm(),
                record.cargoWidthCm(),
                record.cargoHeightCm(),
                record.cargoQuantity(),
                record.cargoDescription()
        );
        TransportCondition transport = new TransportCondition(
                record.originLocation(),
                record.destinationLocation(),
                record.requestedPickupDate(),
                record.requestedDeliveryDate()
        );
        return Booking.register(id, shipperId, cargo, transport);
    }
}
