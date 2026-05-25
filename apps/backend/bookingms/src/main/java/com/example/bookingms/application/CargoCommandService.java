package com.example.bookingms.application;

import com.example.bookingms.domain.commands.BookCargoCommand;
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
}
