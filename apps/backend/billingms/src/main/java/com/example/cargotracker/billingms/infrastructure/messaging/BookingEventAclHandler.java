package com.example.cargotracker.billingms.infrastructure.messaging;

import com.example.cargotracker.billingms.domain.model.commands.CreateInvoiceCommand;
import com.example.cargotracker.shared.events.CargoBookedEvent;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * bookingms から Axon Event Bus 経由で受け取った CargoBookedEvent を
 * PENDING Invoice の作成コマンドに変換する ACL（ADR-0014）。
 *
 * <p>billingms は bookingms に直接依存せず、shared.events パッケージの
 * CargoBookedEvent のみを参照する。予約登録時に PENDING Invoice を自動生成する。</p>
 */
@Component
@Profile("!springboot-integration-test")
public class BookingEventAclHandler {

    private static final Logger LOG = LoggerFactory.getLogger(BookingEventAclHandler.class);

    private final CommandGateway commandGateway;

    public BookingEventAclHandler(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    @EventHandler
    public void on(CargoBookedEvent event) {
        String invoiceId = UUID.randomUUID().toString();
        LOG.info("CargoBookedEvent 受信 bookingId={} → CreateInvoiceCommand invoiceId={}",
                event.bookingId(), invoiceId);
        commandGateway.send(new CreateInvoiceCommand(
                invoiceId,
                event.bookingId(),
                event.shipperId()));
    }
}
