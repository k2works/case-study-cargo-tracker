package com.example.routingms.domain.model;

import com.example.routingms.domain.commands.RegisterVoyageCommand;
import com.example.routingms.domain.commands.UpdateVoyageScheduleCommand;
import com.example.routingms.domain.events.VoyageRegisteredEvent;
import com.example.routingms.domain.events.VoyageScheduleUpdatedEvent;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import java.time.LocalDateTime;

@Aggregate
public class Voyage {

    @AggregateIdentifier
    private String voyageNumber;
    @SuppressWarnings("unused") // Axon Event Sourcing で状態を保持するフィールド
    private LocalDateTime departureDate;
    @SuppressWarnings("unused") // Axon Event Sourcing で状態を保持するフィールド
    private LocalDateTime arrivalDate;

    protected Voyage() {
        // Axon required no-arg constructor
    }

    @CommandHandler
    public Voyage(RegisterVoyageCommand command) {
        if (command.arrivalDate().isBefore(command.departureDate()) ||
                command.arrivalDate().isEqual(command.departureDate())) {
            throw new IllegalArgumentException("到着日は出発日より後でなければなりません");
        }
        AggregateLifecycle.apply(new VoyageRegisteredEvent(
                command.voyageNumber(),
                command.carrierCode(),
                command.carrierName(),
                command.shipName(),
                command.originUnlocode(),
                command.destUnlocode(),
                command.departureDate(),
                command.arrivalDate(),
                command.movements(),
                command.acceptedCargoTypes()
        ));
    }

    @CommandHandler
    public void handle(UpdateVoyageScheduleCommand command) {
        if (command.arrivalDate().isBefore(command.departureDate()) ||
                command.arrivalDate().isEqual(command.departureDate())) {
            throw new IllegalArgumentException("到着日は出発日より後でなければなりません");
        }
        AggregateLifecycle.apply(new VoyageScheduleUpdatedEvent(
                command.voyageNumber(),
                command.departureDate(),
                command.arrivalDate(),
                command.movements(),
                command.acceptedCargoTypes()
        ));
    }

    @EventSourcingHandler
    public void on(VoyageScheduleUpdatedEvent event) {
        this.departureDate = event.departureDate();
        this.arrivalDate = event.arrivalDate();
    }

    @EventSourcingHandler
    public void on(VoyageRegisteredEvent event) {
        this.voyageNumber = event.voyageNumber();
        this.departureDate = event.departureDate();
        this.arrivalDate = event.arrivalDate();
    }

    public String getVoyageNumber() { return voyageNumber; }
}
