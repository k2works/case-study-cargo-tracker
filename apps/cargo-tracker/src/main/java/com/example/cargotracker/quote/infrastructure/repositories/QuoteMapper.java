package com.example.cargotracker.quote.infrastructure.repositories;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface QuoteMapper {

    void insertQuote(@Param("row") QuoteRecord quoteRecord);

    void insertRouteOption(@Param("row") QuoteRouteOptionRecord routeOptionRecord);

    Optional<QuoteRecord> findById(@Param("id") UUID id);

    List<QuoteRouteOptionRecord> findRouteOptionsByQuoteId(@Param("quoteId") UUID quoteId);

    List<QuoteRouteOptionRecord> findRouteOptionsByQuoteIds(@Param("quoteIds") List<UUID> quoteIds);

    List<QuoteRecord> findAll();
}
