package com.example.routingms.application;

import com.example.routingms.domain.commands.RegisterVoyageCommand;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class VoyageCommandService {

    private final CommandGateway commandGateway;

    public VoyageCommandService(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    public CompletableFuture<String> register(RegisterVoyageCommand command) {
        return commandGateway.send(command);
    }
}
