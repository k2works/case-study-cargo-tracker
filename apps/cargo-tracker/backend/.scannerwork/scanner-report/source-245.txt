package com.example.cargotracker.archfixture.compliant.domain.model;

import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;

/** 準拠フィクスチャ: 許可リストの 3 種だけを使う集約。実コードと同じ形で書く。 */
@EventSourced(idType = String.class, tagKey = "fixtureId")
public class CompliantAggregate {

    private String fixtureId;

    @EntityCreator
    public CompliantAggregate() {
    }

    @CommandHandler
    public static String create(String command, EventAppender appender) {
        appender.append(command);
        return command;
    }

    @EventSourcingHandler
    void on(String event) {
        this.fixtureId = event;
    }
}
