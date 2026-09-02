package com.example.cargotracker.archfixture.violating.infrastructure.projection;

import org.axonframework.messaging.commandhandling.gateway.CommandGateway;

/** 違反フィクスチャ: 投影がコマンドを送る（リプレイで副作用が再実行される）。 */
public class CommandSendingProjection {

    private final CommandGateway commandGateway;

    public CommandSendingProjection(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    public void onEvent(Object event) {
        commandGateway.send(event);
    }
}
