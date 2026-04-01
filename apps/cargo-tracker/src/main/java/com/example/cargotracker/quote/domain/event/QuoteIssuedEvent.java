package com.example.cargotracker.quote.domain.event;

import com.example.cargotracker.quote.domain.model.aggregates.QuoteId;
import com.example.cargotracker.quote.domain.model.valueobjects.QuoteNumber;

/**
 * 見積が発行されたときに発行されるドメインイベント。
 */
public record QuoteIssuedEvent(QuoteId quoteId, QuoteNumber quoteNumber) implements DomainEvent {
}
