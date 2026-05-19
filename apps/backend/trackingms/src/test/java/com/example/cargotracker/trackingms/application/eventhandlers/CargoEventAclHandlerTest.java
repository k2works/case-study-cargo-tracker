package com.example.cargotracker.trackingms.application.eventhandlers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.cargotracker.shared.events.CargoTrackedEvent;
import com.example.cargotracker.trackingms.domain.model.commands.InitializeTrackingCommand;
import com.example.cargotracker.trackingms.domain.model.valueobjects.Location;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("CargoEventAclHandler（ユニット）")
class CargoEventAclHandlerTest {

    private final CommandGateway commandGateway = mock(CommandGateway.class);
    private final CargoEventAclHandler handler = new CargoEventAclHandler(commandGateway);

    @Test
    @DisplayName("CargoTrackedEvent を受け取ると InitializeTrackingCommand を発行する")
    void on_CargoTrackedEvent_コマンド発行() {
        var event = new CargoTrackedEvent(
                "550e8400-e29b-41d4-a716-446655440000",
                "TRK-20260101-ABCD1234",
                "JPTYO",
                "DEHAM");

        handler.on(event);

        ArgumentCaptor<InitializeTrackingCommand> captor =
                ArgumentCaptor.forClass(InitializeTrackingCommand.class);
        verify(commandGateway).send(captor.capture());
        var cmd = captor.getValue();
        assert cmd.trackingNumber().equals("TRK-20260101-ABCD1234");
        assert cmd.bookingId().equals("550e8400-e29b-41d4-a716-446655440000");
        assert cmd.origin().equals(Location.of("JPTYO"));
        assert cmd.destination().equals(Location.of("DEHAM"));
    }
}
