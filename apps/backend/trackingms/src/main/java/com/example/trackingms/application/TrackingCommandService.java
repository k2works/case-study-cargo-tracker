package com.example.trackingms.application;

import com.example.trackingms.domain.commands.RegisterTrackingExceptionCommand;
import com.example.trackingms.domain.commands.ResolveTrackingExceptionCommand;
import com.example.trackingms.domain.commands.UpdateTransportStatusCommand;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * 追跡関連の CommandGateway ラッパー（US17 / IT5 2.3、US19 / US20 / IT6 2.3）。
 */
@Service
public class TrackingCommandService {

    private final CommandGateway commandGateway;

    public TrackingCommandService(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    public CompletableFuture<Void> updateStatus(UpdateTransportStatusCommand command) {
        return commandGateway.send(command);
    }

    public CompletableFuture<Void> registerException(RegisterTrackingExceptionCommand command) {
        return commandGateway.send(command);
    }

    public CompletableFuture<Void> resolveException(ResolveTrackingExceptionCommand command) {
        return commandGateway.send(command);
    }
}
