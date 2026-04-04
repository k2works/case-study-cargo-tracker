package com.example.cargotracker.booking.application.internal.commandservices;

import com.example.cargotracker.booking.domain.model.aggregates.Cargo;
import com.example.cargotracker.booking.domain.model.aggregates.CargoType;
import com.example.cargotracker.booking.domain.model.repository.CargoRepository;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.shared.domain.model.Location;
import com.example.cargotracker.shipper.domain.model.repository.ShipperRepository;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class CargoBookingCommandService {

    private static final String SHIPPER_NOT_FOUND = "SHIPPER_NOT_FOUND";

    private final CargoRepository cargoRepository;
    private final ShipperRepository shipperRepository;

    public CargoBookingCommandService(CargoRepository cargoRepository, ShipperRepository shipperRepository) {
        this.cargoRepository = cargoRepository;
        this.shipperRepository = shipperRepository;
    }

    public BookingId bookCargo(BookCargoCommand command) {
        ShipperId shipperId = new ShipperId(UUID.fromString(command.shipperId()));
        if (shipperRepository.findById(shipperId).isEmpty()) {
            throw new IllegalArgumentException(SHIPPER_NOT_FOUND);
        }

        Cargo cargo = new Cargo(
                new BookingId(UUID.randomUUID()),
                shipperId,
                CargoType.valueOf(command.cargoType()),
                command.weight(),
                new RouteSpecification(
                        new Location(command.originUnlocode()),
                        new Location(command.destinationUnlocode()),
                        command.arrivalDeadline()
                )
        );
        cargoRepository.save(cargo);
        return cargo.getBookingId();
    }
}
