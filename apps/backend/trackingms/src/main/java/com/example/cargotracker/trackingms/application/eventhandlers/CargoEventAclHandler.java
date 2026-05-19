package com.example.cargotracker.trackingms.application.eventhandlers;

import com.example.cargotracker.shared.events.CargoTrackedEvent;
import com.example.cargotracker.trackingms.domain.model.commands.InitializeTrackingCommand;
import com.example.cargotracker.trackingms.domain.model.valueobjects.Location;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * bookingms の CargoTrackedEvent を購読し、InitializeTrackingCommand を内部発行する ACL。
 *
 * <p>ADR-0012 / ADR-0014 に基づき、trackingms は bookingms に直接依存せず、
 * shared.events パッケージの Event クラスのみを参照する。</p>
 */
@Component
@Profile("!springboot-integration-test")
public class CargoEventAclHandler {

    private final CommandGateway commandGateway;

    public CargoEventAclHandler(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    @EventHandler
    public void on(CargoTrackedEvent event) {
        commandGateway.send(new InitializeTrackingCommand(
                event.trackingNumber(),
                event.bookingId(),
                null,
                Location.of(event.originUnlocode()),
                Location.of(event.destinationUnlocode()),
                null));
    }
}
