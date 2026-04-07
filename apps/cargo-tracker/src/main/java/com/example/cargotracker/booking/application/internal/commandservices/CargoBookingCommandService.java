package com.example.cargotracker.booking.application.internal.commandservices;

import com.example.cargotracker.booking.application.internal.outboundservices.ShipperExistenceChecker;
import com.example.cargotracker.booking.domain.model.aggregates.Cargo;
import com.example.cargotracker.booking.domain.model.aggregates.CargoType;
import com.example.cargotracker.booking.domain.model.aggregates.BookingStatus;
import com.example.cargotracker.booking.domain.model.events.BookingCancelledEvent;
import com.example.cargotracker.booking.domain.model.events.BookingConfirmedEvent;
import com.example.cargotracker.booking.domain.model.exceptions.BookingNotFoundException;
import com.example.cargotracker.booking.domain.model.exceptions.ShipperNotFoundException;
import com.example.cargotracker.booking.domain.model.repository.CargoRepository;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import org.springframework.context.ApplicationEventPublisher;
import com.example.cargotracker.booking.domain.model.valueobjects.Description;
import com.example.cargotracker.booking.domain.model.valueobjects.Dimensions;
import com.example.cargotracker.booking.domain.model.valueobjects.HazardousDeclaration;
import com.example.cargotracker.booking.domain.model.valueobjects.Quantity;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.TemperatureRequirement;
import com.example.cargotracker.booking.domain.model.valueobjects.TemperatureUnit;
import com.example.cargotracker.shared.domain.model.ShipperId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class CargoBookingCommandService {

    private final CargoRepository cargoRepository;
    private final ShipperExistenceChecker shipperExistenceChecker;
    private final ApplicationEventPublisher eventPublisher;

    public CargoBookingCommandService(CargoRepository cargoRepository, ShipperExistenceChecker shipperExistenceChecker,
                                      ApplicationEventPublisher eventPublisher) {
        this.cargoRepository = cargoRepository;
        this.shipperExistenceChecker = shipperExistenceChecker;
        this.eventPublisher = eventPublisher;
    }

    public BookingId bookCargo(BookCargoCommand command) {
        ShipperId shipperId = new ShipperId(UUID.fromString(command.shipperId()));
        if (!shipperExistenceChecker.exists(shipperId)) {
            throw new ShipperNotFoundException(shipperId);
        }

        Dimensions dimensions = toDimensions(command);
        Quantity quantity = command.quantity() != null ? new Quantity(command.quantity()) : null;
        Description description = command.description() != null ? new Description(command.description()) : null;

        Cargo cargo = new Cargo(
                new BookingId(UUID.randomUUID()),
                shipperId,
                CargoType.valueOf(command.cargoType()),
                command.weight(),
                Cargo.state(
                        RouteSpecification.fromUnLocodes(
                                command.originUnlocode(),
                                command.destinationUnlocode(),
                                command.arrivalDeadline()
                        ),
                        BookingStatus.PRELIMINARY,
                        Cargo.details(dimensions, quantity, description),
                        Cargo.handling(toHazardousDeclaration(command), toTemperatureRequirement(command))
                )
        );
        cargoRepository.save(cargo);
        return cargo.getBookingId();
    }

    private Dimensions toDimensions(BookCargoCommand command) {
        if (command.dimensionLength() != null && command.dimensionWidth() != null && command.dimensionHeight() != null) {
            return new Dimensions(command.dimensionLength(), command.dimensionWidth(), command.dimensionHeight());
        }
        return null;
    }

    private HazardousDeclaration toHazardousDeclaration(BookCargoCommand command) {
        if (hasText(command.hazardousClass()) && hasText(command.unNumber()) && hasText(command.properShippingName())) {
            return new HazardousDeclaration(command.hazardousClass(), command.unNumber(), command.properShippingName());
        }
        return null;
    }

    private TemperatureRequirement toTemperatureRequirement(BookCargoCommand command) {
        if (command.minTemperature() != null && command.maxTemperature() != null && hasText(command.temperatureUnit())) {
            return new TemperatureRequirement(command.minTemperature(), command.maxTemperature(), TemperatureUnit.valueOf(command.temperatureUnit()));
        }
        return null;
    }

    public void confirmBooking(ConfirmBookingCommand command) {
        BookingId bookingId = new BookingId(UUID.fromString(command.bookingId()));
        Cargo cargo = cargoRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        Cargo confirmed = cargo.confirm();
        cargoRepository.updateStatus(confirmed);
        eventPublisher.publishEvent(new BookingConfirmedEvent(confirmed.getBookingId()));
    }

    public void cancelBooking(CancelBookingCommand command) {
        BookingId bookingId = new BookingId(UUID.fromString(command.bookingId()));
        Cargo cargo = cargoRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        Cargo cancelled = cargo.cancel();
        cargoRepository.updateStatus(cancelled);
        eventPublisher.publishEvent(new BookingCancelledEvent(cancelled.getBookingId()));
    }

    public void assignToRouting(AssignToRoutingCommand command) {
        BookingId bookingId = new BookingId(UUID.fromString(command.bookingId()));
        Cargo cargo = cargoRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        Cargo routed = cargo.assignToRouting();
        cargoRepository.updateStatus(routed);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
