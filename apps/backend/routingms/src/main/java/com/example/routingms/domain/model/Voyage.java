package com.example.routingms.domain.model;

import com.example.routingms.domain.commands.RegisterVoyageCommand;
import com.example.routingms.domain.events.VoyageRegisteredEvent;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import java.time.LocalDateTime;
import java.util.List;

@Aggregate
public class Voyage {

    @AggregateIdentifier
    private String voyageNumber;
    private LocalDateTime departureDate;
    private LocalDateTime arrivalDate;

    protected Voyage() {
        // Axon required no-arg constructor
    }

    @CommandHandler
    public Voyage(RegisterVoyageCommand command) {
        if (command.getArrivalDate().isBefore(command.getDepartureDate()) ||
                command.getArrivalDate().isEqual(command.getDepartureDate())) {
            throw new IllegalArgumentException("到着日は出発日より後でなければなりません");
        }
        AggregateLifecycle.apply(new VoyageRegisteredEvent(
                command.getVoyageNumber(),
                command.getCarrierCode(),
                command.getCarrierName(),
                command.getShipName(),
                command.getOriginUnlocode(),
                command.getDestUnlocode(),
                command.getDepartureDate(),
                command.getArrivalDate(),
                command.getMovements(),
                command.getAcceptedCargoTypes()
        ));
    }

    @EventSourcingHandler
    public void on(VoyageRegisteredEvent event) {
        this.voyageNumber = event.getVoyageNumber();
        this.departureDate = event.getDepartureDate();
        this.arrivalDate = event.getArrivalDate();
    }

    public String getVoyageNumber() { return voyageNumber; }
}
