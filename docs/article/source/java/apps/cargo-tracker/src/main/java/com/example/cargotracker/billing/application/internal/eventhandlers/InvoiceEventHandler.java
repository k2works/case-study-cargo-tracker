package com.example.cargotracker.billing.application.internal.eventhandlers;

import com.example.cargotracker.billing.application.internal.commandservices.GenerateInvoiceCommand;
import com.example.cargotracker.billing.application.internal.commandservices.InvoiceCommandService;
import com.example.cargotracker.booking.domain.model.events.CargoRoutedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class InvoiceEventHandler {

    private static final Logger log = LoggerFactory.getLogger(InvoiceEventHandler.class);

    private final InvoiceCommandService invoiceCommandService;

    public InvoiceEventHandler(InvoiceCommandService invoiceCommandService) {
        this.invoiceCommandService = invoiceCommandService;
    }

    @EventListener
    public void onCargoRouted(CargoRoutedEvent event) {
        var command = new GenerateInvoiceCommand(
                event.bookingId().toString(),
                event.shipperId(),
                event.totalBaseFare()
        );
        var invoiceId = invoiceCommandService.generateInvoice(command);
        log.info("[精算] 予約 {} の精算書を発行しました。請求番号: {}", event.bookingId(), invoiceId);
    }
}
