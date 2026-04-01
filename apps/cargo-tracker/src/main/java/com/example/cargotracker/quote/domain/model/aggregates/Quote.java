package com.example.cargotracker.quote.domain.model.aggregates;

import com.example.cargotracker.quote.domain.event.DomainEvent;
import com.example.cargotracker.quote.domain.event.QuoteIssuedEvent;
import com.example.cargotracker.quote.domain.model.valueobjects.QuoteCondition;
import com.example.cargotracker.quote.domain.model.valueobjects.QuoteNumber;
import com.example.cargotracker.quote.domain.model.valueobjects.RouteOption;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 見積集約ルート。
 */
public class Quote {

    private final QuoteId id;
    private final QuoteNumber quoteNumber;
    private final QuoteCondition condition;
    private final List<RouteOption> routeOptions;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private Quote(QuoteId id, QuoteNumber quoteNumber,
                  QuoteCondition condition, List<RouteOption> routeOptions) {
        this.id = id;
        this.quoteNumber = quoteNumber;
        this.condition = condition;
        this.routeOptions = Collections.unmodifiableList(routeOptions);
    }

    /**
     * 見積を発行する。
     */
    public static Quote issue(QuoteId id, QuoteCondition condition, List<RouteOption> routeOptions) {
        if (id == null) throw new IllegalArgumentException("見積 ID は null にできません");
        if (condition == null) throw new IllegalArgumentException("見積条件は null にできません");
        if (routeOptions == null) throw new IllegalArgumentException("ルート候補リストは null にできません");
        if (routeOptions.isEmpty()) throw new IllegalArgumentException("ルート候補は 1 件以上必要です");

        QuoteNumber quoteNumber = QuoteNumber.generate(UUID.randomUUID());
        Quote quote = new Quote(id, quoteNumber, condition, new ArrayList<>(routeOptions));
        quote.domainEvents.add(new QuoteIssuedEvent(id, quoteNumber));
        return quote;
    }

    /**
     * 永続化ストアから見積を再構成する。ドメインイベントは発行しない。
     */
    public static Quote reconstitute(QuoteId id, QuoteNumber quoteNumber,
                                     QuoteCondition condition, List<RouteOption> routeOptions) {
        return new Quote(id, quoteNumber, condition, new ArrayList<>(routeOptions));
    }

    public QuoteId getId() { return id; }
    public QuoteNumber getQuoteNumber() { return quoteNumber; }
    public QuoteCondition getCondition() { return condition; }
    public List<RouteOption> getRouteOptions() { return routeOptions; }

    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }
}
