package com.example.cargotracker.booking.interfaces.rest.transform;

import com.example.cargotracker.booking.domain.model.aggregates.Cargo;
import com.example.cargotracker.booking.interfaces.rest.dto.CargoResponse;
import org.springframework.stereotype.Component;

@Component
public class CargoAssembler {

    public CargoResponse toResponse(Cargo cargo) {
        return new CargoResponse(
                cargo.getBookingId().toString(),
                cargo.getShipperId().toString(),
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
                cargo.getStatus().getBadgeColor()
        );
    }
}
