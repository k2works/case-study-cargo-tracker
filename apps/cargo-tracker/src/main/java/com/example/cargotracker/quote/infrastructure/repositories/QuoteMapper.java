package com.example.cargotracker.quote.infrastructure.repositories;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface QuoteMapper {

    void insertQuote(@Param("row") QuoteRecord record);

    void insertRouteOption(@Param("row") QuoteRouteOptionRecord record);

    Optional<QuoteRecord> findById(@Param("id") UUID id);

    List<QuoteRouteOptionRecord> findRouteOptionsByQuoteId(@Param("quoteId") UUID quoteId);

    List<QuoteRecord> findAll();
}
