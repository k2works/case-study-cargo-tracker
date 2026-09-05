package com.example.cargotracker.archfixture.compliant.application.reaction;

import org.axonframework.messaging.commandhandling.gateway.CommandGateway;

/** 準拠フィクスチャ: Reaction Handler はコマンドを送ってよい。 */
public class CompliantReactionHandler {

    private final CommandGateway commandGateway;

    public CompliantReactionHandler(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    public void on(Object event) {
        commandGateway.send(event);
    }
}
