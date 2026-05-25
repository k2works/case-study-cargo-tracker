package com.example.bookingms.application;

import com.example.bookingms.domain.commands.CreateQuotationCommand;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * 見積コマンドの CommandGateway ラッパー（US01）。
 */
@Service
public class QuotationCommandService {

    private final CommandGateway commandGateway;

    public QuotationCommandService(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    public CompletableFuture<String> create(CreateQuotationCommand command) {
        return commandGateway.send(command);
    }
}
