package com.example.routingms.interfaces.events;

import com.example.routingms.domain.commands.RegisterVoyageCommand.CarrierMovementData;
import com.example.routingms.domain.events.VoyageRegisteredEvent;
import com.example.routingms.domain.events.VoyageScheduleUpdatedEvent;
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
                event.voyageNumber(),
                event.carrierCode(),
                event.carrierName(),
                event.shipName(),
                event.originUnlocode(),
                event.destUnlocode(),
                event.departureDate(),
                event.arrivalDate()
        );

        List<CarrierMovementData> movements = event.movements();
        for (int i = 0; i < movements.size(); i++) {
            CarrierMovementData m = movements.get(i);
            voyageMapper.insertCarrierMovement(
                    event.voyageNumber(),
                    i,
                    m.departureUnlocode(),
                    m.arrivalUnlocode(),
                    m.departureTime(),
                    m.arrivalTime()
            );
        }

        for (String cargoType : event.acceptedCargoTypes()) {
            voyageMapper.insertAcceptedCargoType(event.voyageNumber(), cargoType);
        }
    }

    @EventHandler
    public void on(VoyageScheduleUpdatedEvent event) {
        voyageMapper.updateVoyage(event.voyageNumber(), event.departureDate(), event.arrivalDate());

        voyageMapper.deleteCarrierMovements(event.voyageNumber());
        List<CarrierMovementData> movements = event.movements();
        for (int i = 0; i < movements.size(); i++) {
            CarrierMovementData m = movements.get(i);
            voyageMapper.insertCarrierMovement(
                    event.voyageNumber(),
                    i,
                    m.departureUnlocode(),
                    m.arrivalUnlocode(),
                    m.departureTime(),
                    m.arrivalTime()
            );
        }

        voyageMapper.deleteAcceptedCargoTypes(event.voyageNumber());
        for (String cargoType : event.acceptedCargoTypes()) {
            voyageMapper.insertAcceptedCargoType(event.voyageNumber(), cargoType);
        }
    }
}
