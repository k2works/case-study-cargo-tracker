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
                cargo.getWeight(),
                cargo.getRouteSpecification().getOrigin().getUnlocode(),
                cargo.getRouteSpecification().getDestination().getUnlocode(),
                cargo.getRouteSpecification().getArrivalDeadline(),
                cargo.getStatus().name()
        );
    }
}
