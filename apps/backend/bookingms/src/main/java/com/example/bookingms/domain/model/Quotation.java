package com.example.bookingms.domain.model;

import com.example.bookingms.domain.commands.CreateQuotationCommand;
import com.example.bookingms.domain.events.QuotationCreatedEvent;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import java.time.LocalDate;

/**
 * 輸送見積集約（US01 / Booking Context）。
 *
 * <p>Event Sourcing で永続化される Aggregate Root。状態は QuotationCreatedEvent から再生される。
 * ルート候補が空（期限内に到達可能な航海が無い）でも見積自体は作成でき、
 * 候補なしであることが荷主への通知材料となる。</p>
 */
@Aggregate
public class Quotation {

    @AggregateIdentifier
    private String quotationId;
    @SuppressWarnings("unused") // Axon Event Sourcing で状態を保持するフィールド
    private String status;

    protected Quotation() {
        // Axon required no-arg constructor
    }

    @CommandHandler
    public Quotation(CreateQuotationCommand command) {
        validate(command);
        AggregateLifecycle.apply(new QuotationCreatedEvent(
                command.quotationId(),
                command.shipperId(),
                command.routeSpec(),
                command.cargoSpec(),
                command.candidateRoutes(),
                command.estimatedAmount(),
                command.estimatedCurrency(),
                command.validUntil(),
                "DRAFT"
        ));
    }

    private void validate(CreateQuotationCommand command) {
        if (command.quotationId() == null || command.quotationId().isBlank()) {
            throw new IllegalArgumentException("見積 ID は必須です");
        }
        if (command.shipperId() == null || command.shipperId().isBlank()) {
            throw new IllegalArgumentException("荷主 ID は必須です");
        }
        validateRouteSpec(command.routeSpec());
        if (command.validUntil() == null) {
            throw new IllegalArgumentException("見積有効期限は必須です");
        }
        if (command.validUntil().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("見積有効期限は今日以降である必要があります");
        }
    }

    private void validateRouteSpec(RouteSpecification routeSpec) {
        if (routeSpec == null) {
            throw new IllegalArgumentException("経路仕様は必須です");
        }
        if (routeSpec.originUnlocode() == null || routeSpec.originUnlocode().isBlank()) {
            throw new IllegalArgumentException("出発地は必須です");
        }
        if (routeSpec.destinationUnlocode() == null || routeSpec.destinationUnlocode().isBlank()) {
            throw new IllegalArgumentException("目的地は必須です");
        }
        if (routeSpec.originUnlocode().equals(routeSpec.destinationUnlocode())) {
            throw new IllegalArgumentException("出発地と目的地は異なる必要があります");
        }
        if (routeSpec.arrivalDeadline() == null) {
            throw new IllegalArgumentException("到着期限は必須です");
        }
    }

    @EventSourcingHandler
    public void on(QuotationCreatedEvent event) {
        this.quotationId = event.quotationId();
        this.status = event.status();
    }

    public String getQuotationId() {
        return quotationId;
    }
}
