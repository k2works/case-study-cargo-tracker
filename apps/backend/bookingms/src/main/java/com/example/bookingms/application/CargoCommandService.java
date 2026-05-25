package com.example.bookingms.application;

import com.example.bookingms.domain.commands.BookCargoCommand;
import com.example.bookingms.domain.commands.CancelBookingCommand;
import com.example.bookingms.domain.commands.ConfirmBookingCommand;
import com.example.bookingms.domain.commands.RequestRouteDesignCommand;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * 貨物予約コマンドの CommandGateway ラッパー（US04）。
 */
@Service
public class CargoCommandService {

    private final CommandGateway commandGateway;

    public CargoCommandService(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    public CompletableFuture<String> book(BookCargoCommand command) {
        return commandGateway.send(command);
    }

    public CompletableFuture<Void> requestRouteDesign(RequestRouteDesignCommand command) {
        return commandGateway.send(command);
    }

    public CompletableFuture<Void> confirm(ConfirmBookingCommand command) {
        return commandGateway.send(command);
    }

    public CompletableFuture<Void> cancel(CancelBookingCommand command) {
        return commandGateway.send(command);
    }
}
