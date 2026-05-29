package com.example.handlingms.application;

import com.example.handlingms.domain.commands.RegisterHandlingActivityCommand;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * 荷役作業コマンドの CommandGateway ラッパー（US15・US16 / IT5 3.x）。
 */
@Service
public class HandlingActivityCommandService {

    private final CommandGateway commandGateway;

    public HandlingActivityCommandService(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    public CompletableFuture<String> register(RegisterHandlingActivityCommand command) {
        return commandGateway.send(command);
    }
}
