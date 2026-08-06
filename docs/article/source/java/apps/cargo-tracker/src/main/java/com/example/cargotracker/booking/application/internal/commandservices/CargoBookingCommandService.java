package com.example.cargotracker.booking.application.internal.commandservices;

import com.example.cargotracker.booking.application.internal.outboundservices.ShipperExistenceChecker;
import com.example.cargotracker.booking.application.internal.outboundservices.acl.TrackingPort;
import com.example.cargotracker.booking.domain.model.aggregates.Cargo;
import com.example.cargotracker.booking.domain.model.aggregates.CargoType;
import com.example.cargotracker.booking.domain.model.aggregates.BookingStatus;
import com.example.cargotracker.booking.domain.model.events.BookingAssignedToRoutingEvent;
import com.example.cargotracker.booking.domain.model.events.BookingCancelledEvent;
import com.example.cargotracker.booking.domain.model.events.BookingConfirmedEvent;
import com.example.cargotracker.booking.domain.model.events.CargoRoutedEvent;
import com.example.cargotracker.booking.domain.model.exceptions.BookingNotFoundException;
import com.example.cargotracker.booking.domain.model.exceptions.ShipperNotFoundException;
import com.example.cargotracker.booking.domain.model.repository.CargoRepository;
import com.example.cargotracker.booking.domain.model.repository.LegRepository;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoItinerary;
import com.example.cargotracker.booking.domain.model.valueobjects.Leg;
import org.springframework.context.ApplicationEventPublisher;
import com.example.cargotracker.booking.domain.model.valueobjects.Description;
import com.example.cargotracker.booking.domain.model.valueobjects.Dimensions;
import com.example.cargotracker.booking.domain.model.valueobjects.HazardousDeclaration;
import com.example.cargotracker.booking.domain.model.valueobjects.Quantity;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.TemperatureRequirement;
import com.example.cargotracker.booking.domain.model.valueobjects.TemperatureUnit;
import com.example.cargotracker.routing.domain.model.Voyage;
import com.example.cargotracker.routing.domain.model.repository.VoyageRepository;
import com.example.cargotracker.routing.domain.model.valueobjects.VoyageNumber;
import com.example.cargotracker.shared.domain.model.ShipperId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CargoBookingCommandService {

    private final CargoRepository cargoRepository;
    private final ShipperExistenceChecker shipperExistenceChecker;
    private final ApplicationEventPublisher eventPublisher;
    private final VoyageRepository voyageRepository;
    private final LegRepository legRepository;
    private final TrackingPort trackingPort;

    public CargoBookingCommandService(CargoRepository cargoRepository, ShipperExistenceChecker shipperExistenceChecker,
                                      ApplicationEventPublisher eventPublisher,
                                      VoyageRepository voyageRepository, LegRepository legRepository,
                                      TrackingPort trackingPort) {
        this.cargoRepository = cargoRepository;
        this.shipperExistenceChecker = shipperExistenceChecker;
        this.eventPublisher = eventPublisher;
        this.voyageRepository = voyageRepository;
        this.legRepository = legRepository;
        this.trackingPort = trackingPort;
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

    public void assignItinerary(RouteCargoCommand command) {
        BookingId bookingId = new BookingId(UUID.fromString(command.bookingId()));
        Cargo cargo = cargoRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        Voyage voyage = voyageRepository.findByVoyageNumber(new VoyageNumber(command.voyageNumber()))
                .orElseThrow(() -> new IllegalArgumentException("Voyage not found: " + command.voyageNumber()));
        Leg leg = new Leg(
                voyage.getVoyageNumber().number(),
                voyage.getDepartureLocation(),
                voyage.getArrivalLocation(),
                voyage.getDepartureTime(),
                voyage.getArrivalTime()
        );
        CargoItinerary itinerary = new CargoItinerary(List.of(leg));
        Cargo routed = cargo.assignItinerary(itinerary);
        cargoRepository.updateStatus(routed);
        legRepository.saveAll(bookingId, itinerary.legs());
        eventPublisher.publishEvent(new CargoRoutedEvent(
                routed.getBookingId(),
                routed.getShipperId().toString(),
                voyage.getTotalBaseFare()
        ));
    }

    public String issueTrackingNumber(IssueTrackingCommand command) {
        BookingId bookingId = new BookingId(UUID.fromString(command.bookingId()));
        Cargo cargo = cargoRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        String trackingNumber = trackingPort.issueTrackingNumber(command.bookingId());
        Cargo tracked = cargo.issueTracking(trackingNumber);
        cargoRepository.updateTrackingNumber(tracked);
        return trackingNumber;
    }

    public void settleBooking(SettleBookingCommand command) {
        BookingId bookingId = new BookingId(UUID.fromString(command.bookingId()));
        Cargo cargo = cargoRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        Cargo settled = cargo.settle();
        cargoRepository.updateStatus(settled);
    }

    public void assignToRouting(AssignToRoutingCommand command) {
        BookingId bookingId = new BookingId(UUID.fromString(command.bookingId()));
        Cargo cargo = cargoRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        Cargo routed = cargo.assignToRouting();
        cargoRepository.updateStatus(routed);
        eventPublisher.publishEvent(new BookingAssignedToRoutingEvent(routed.getBookingId()));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
