package com.example.cargotracker.booking.interfaces.rest.transform;

import com.example.cargotracker.booking.domain.model.aggregates.Cargo;
import com.example.cargotracker.booking.interfaces.rest.dto.CargoResponse;
import com.example.cargotracker.shipper.domain.model.repository.ShipperRepository;
import org.springframework.stereotype.Component;

@Component
public class CargoAssembler {

    private final ShipperRepository shipperRepository;

    public CargoAssembler(ShipperRepository shipperRepository) {
        this.shipperRepository = shipperRepository;
    }

    public CargoResponse toResponse(Cargo cargo) {
        String shipperName = shipperRepository.findById(cargo.getShipperId())
                .map(s -> s.getName().value())
                .orElse(null);
        return new CargoResponse(
                cargo.getBookingId().toString(),
                cargo.getShipperId().toString(),
                shipperName,
                cargo.getCargoType().name(),
                cargo.getCargoType().getDisplayName(),
                cargo.getWeight(),
                cargo.getDimensions() != null ? cargo.getDimensions().length() : null,
                cargo.getDimensions() != null ? cargo.getDimensions().width() : null,
                cargo.getDimensions() != null ? cargo.getDimensions().height() : null,
                cargo.getQuantity() != null ? cargo.getQuantity().value() : null,
                cargo.getDescription() != null ? cargo.getDescription().value() : null,
                cargo.getRouteSpecification().origin().unlocode(),
                cargo.getRouteSpecification().destination().unlocode(),
                cargo.getRouteSpecification().arrivalDeadline(),
                cargo.getStatus().name(),
                cargo.getStatus().getDisplayName(),
                cargo.getStatus().getBadgeColor(),
                cargo.getHazardousDeclaration() != null ? cargo.getHazardousDeclaration().hazardousClass() : null,
                cargo.getHazardousDeclaration() != null ? cargo.getHazardousDeclaration().unNumber() : null,
                cargo.getHazardousDeclaration() != null ? cargo.getHazardousDeclaration().properShippingName() : null,
                cargo.getTemperatureRequirement() != null ? cargo.getTemperatureRequirement().minTemperature() : null,
                cargo.getTemperatureRequirement() != null ? cargo.getTemperatureRequirement().maxTemperature() : null,
                cargo.getTemperatureRequirement() != null ? cargo.getTemperatureRequirement().unit().name() : null,
                cargo.getTrackingNumber()
        );
    }
}
