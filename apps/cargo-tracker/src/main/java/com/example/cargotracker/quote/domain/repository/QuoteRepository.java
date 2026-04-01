package com.example.cargotracker.quote.domain.repository;

import com.example.cargotracker.quote.domain.model.aggregates.Quote;
import com.example.cargotracker.quote.domain.model.aggregates.QuoteId;

import java.util.List;
import java.util.Optional;

/**
 * Quote 集約のリポジトリインターフェース。
 */
public interface QuoteRepository {

    void save(Quote quote);

    Optional<Quote> findById(QuoteId id);

    List<Quote> findAll();
}
