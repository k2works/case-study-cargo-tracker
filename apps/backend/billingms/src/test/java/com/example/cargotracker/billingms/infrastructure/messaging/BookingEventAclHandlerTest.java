package com.example.cargotracker.billingms.infrastructure.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.cargotracker.billingms.domain.model.commands.CreateInvoiceCommand;
import com.example.cargotracker.shared.events.CargoBookedEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BookingEventAclHandler 単体テスト")
class BookingEventAclHandlerTest {

    private CommandGateway commandGateway;
    private BookingEventAclHandler handler;

    @BeforeEach
    void setUp() {
        commandGateway = mock(CommandGateway.class);
        handler = new BookingEventAclHandler(commandGateway);
    }

    @Test
    @DisplayName("CargoBookedEvent 受信で CreateInvoiceCommand が送信される")
    void on_CargoBookedEvent() {
        var event = new CargoBookedEvent(
                "B-2026-0001", "SHP-001", "JPTYO", "USNYC",
                "GENERAL", LocalDate.of(2026, 9, 30),
                new BigDecimal("100"), 100, 80, 80, 1,
                "Test Cargo", null, null, null, null, null);
        handler.on(event);
        verify(commandGateway).send(any(CreateInvoiceCommand.class));
    }
}
