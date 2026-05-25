package com.example.bookingms.application;

import com.example.bookingms.domain.commands.RegisterShipperCommand;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * 荷主登録の CommandGateway ラッパー（US02）。
 *
 * <p>REST Controller から呼び出され、Axon の {@link CommandGateway} を通じて
 * {@link RegisterShipperCommand} を Shipper Aggregate に送信する。</p>
 */
@Service
public class ShipperCommandService {

    private final CommandGateway commandGateway;

    public ShipperCommandService(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    public CompletableFuture<String> register(RegisterShipperCommand command) {
        return commandGateway.send(command);
    }
}
