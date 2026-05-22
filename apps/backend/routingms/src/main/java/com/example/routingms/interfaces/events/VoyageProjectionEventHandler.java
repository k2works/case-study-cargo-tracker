package com.example.routingms.interfaces.events;

import com.example.routingms.domain.commands.RegisterVoyageCommand.CarrierMovementData;
import com.example.routingms.domain.events.VoyageRegisteredEvent;
import com.example.routingms.infrastructure.repositories.mybatis.VoyageMapper;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VoyageProjectionEventHandler {

    private final VoyageMapper voyageMapper;

    public VoyageProjectionEventHandler(VoyageMapper voyageMapper) {
        this.voyageMapper = voyageMapper;
    }

    @EventHandler
    public void on(VoyageRegisteredEvent event) {
        voyageMapper.insertVoyage(
                event.getVoyageNumber(),
                event.getCarrierCode(),
                event.getCarrierName(),
                event.getShipName(),
                event.getOriginUnlocode(),
                event.getDestUnlocode(),
                event.getDepartureDate(),
                event.getArrivalDate()
        );

        List<CarrierMovementData> movements = event.getMovements();
        for (int i = 0; i < movements.size(); i++) {
            CarrierMovementData m = movements.get(i);
            voyageMapper.insertCarrierMovement(
                    event.getVoyageNumber(),
                    i,
                    m.departureUnlocode(),
                    m.arrivalUnlocode(),
                    m.departureTime(),
                    m.arrivalTime()
            );
        }

        for (String cargoType : event.getAcceptedCargoTypes()) {
            voyageMapper.insertAcceptedCargoType(event.getVoyageNumber(), cargoType);
        }
    }
}
