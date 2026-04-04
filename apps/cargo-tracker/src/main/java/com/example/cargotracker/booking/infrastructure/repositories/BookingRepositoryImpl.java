package com.example.cargotracker.booking.infrastructure.repositories;

import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.AssignedRoute;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingLeg;
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
import java.util.UUID;

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
        AssignedRoute ar = booking.getAssignedRoute();

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
                cargo.unNumber(),
                cargo.hazardClass(),
                cargo.minTempCelsius(),
                cargo.maxTempCelsius(),
                transport.originLocation(),
                transport.destinationLocation(),
                transport.requestedPickupDate(),
                transport.requestedDeliveryDate(),
                booking.getStatus().name(),
                ar != null ? ar.voyageNumber() : null,
                ar != null ? ar.routePath() : null,
                ar != null ? ar.estimatedArrival() : null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        if (bookingMapper.findById(booking.getId().value()).isPresent()) {
            bookingMapper.update(row);
        } else {
            bookingMapper.insert(row);
        }

        UUID bookingUuid = booking.getId().value();
        bookingMapper.deleteLegsByBookingId(bookingUuid);
        for (BookingLeg leg : booking.getLegs()) {
            BookingLegRecord legRow = new BookingLegRecord(
                    null,
                    bookingUuid,
                    leg.voyageNumber(),
                    leg.originLocode(),
                    leg.destinationLocode(),
                    leg.departureDate(),
                    leg.arrivalDate(),
                    leg.legOrder()
            );
            bookingMapper.insertLeg(legRow);
        }
    }

    @Override
    public Optional<Booking> findById(BookingId id) {
        return bookingMapper.findById(id.value())
                .map(row -> toBooking(row, bookingMapper.findLegsByBookingId(id.value())));
    }

    @Override
    public List<Booking> findAll() {
        return bookingMapper.findAll().stream()
                .map(row -> toBooking(row, bookingMapper.findLegsByBookingId(row.id())))
                .toList();
    }

    private Booking toBooking(BookingRecord row, List<BookingLegRecord> legRows) {
        BookingId id = new BookingId(row.id());
        ShipperId shipperId = new ShipperId(row.shipperId());
        CargoSpecification cargo = new CargoSpecification(
                CargoType.valueOf(row.cargoType()),
                row.cargoWeightKg(),
                new CargoSpecification.CargoDimensions(
                        row.cargoLengthCm(),
                        row.cargoWidthCm(),
                        row.cargoHeightCm()
                ),
                row.cargoQuantity(),
                row.cargoDescription(),
                new CargoSpecification.SpecialHandling(
                        row.cargoUnNumber(),
                        row.cargoHazardClass(),
                        row.cargoMinTempCelsius(),
                        row.cargoMaxTempCelsius()
                )
        );
        TransportCondition transport = new TransportCondition(
                row.originLocation(),
                row.destinationLocation(),
                row.requestedPickupDate(),
                row.requestedDeliveryDate()
        );
        AssignedRoute assignedRoute = null;
        if (row.assignedVoyageNo() != null) {
            assignedRoute = new AssignedRoute(
                    row.assignedVoyageNo(),
                    row.routePath(),
                    row.estimatedArrival()
            );
        }
        List<BookingLeg> legs = legRows.stream()
                .map(lr -> new BookingLeg(
                        lr.voyageNumber(),
                        lr.originLocode(),
                        lr.destinationLocode(),
                        lr.departureDate(),
                        lr.arrivalDate(),
                        lr.legOrder()
                ))
                .toList();
        return Booking.reconstitute(id, shipperId, cargo, transport,
                BookingStatus.valueOf(row.status()), assignedRoute, legs);
    }
}
