package com.example.cargotracker.booking.infrastructure.repositories;

import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.booking.domain.model.valueobjects.TransportCondition;
import com.example.cargotracker.booking.domain.repository.BookingRepository;
import com.example.cargotracker.shared.domain.model.ShipperId;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
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

        BookingRecord row = new BookingRecord(
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
        bookingMapper.insert(row);
    }

    @Override
    public Optional<Booking> findById(BookingId id) {
        return bookingMapper.findById(id.value())
                .map(this::toBooking);
    }

    @Override
    public List<Booking> findAll() {
        return bookingMapper.findAll().stream()
                .map(this::toBooking)
                .toList();
    }

    private Booking toBooking(BookingRecord row) {
        BookingId id = new BookingId(row.id());
        ShipperId shipperId = new ShipperId(row.shipperId());
        CargoSpecification cargo = new CargoSpecification(
                CargoType.valueOf(row.cargoType()),
                row.cargoWeightKg(),
                row.cargoLengthCm(),
                row.cargoWidthCm(),
                row.cargoHeightCm(),
                row.cargoQuantity(),
                row.cargoDescription()
        );
        TransportCondition transport = new TransportCondition(
                row.originLocation(),
                row.destinationLocation(),
                row.requestedPickupDate(),
                row.requestedDeliveryDate()
        );
        return Booking.reconstitute(id, shipperId, cargo, transport, BookingStatus.valueOf(row.status()));
    }
}
