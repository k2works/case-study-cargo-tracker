package com.example.cargotracker.quote.infrastructure.repositories;

import com.example.cargotracker.quote.domain.model.aggregates.Quote;
import com.example.cargotracker.quote.domain.model.aggregates.QuoteId;
import com.example.cargotracker.quote.domain.model.valueobjects.CargoType;
import com.example.cargotracker.quote.domain.model.valueobjects.QuoteCondition;
import com.example.cargotracker.quote.domain.model.valueobjects.QuoteNumber;
import com.example.cargotracker.quote.domain.model.valueobjects.RouteOption;
import com.example.cargotracker.quote.domain.repository.QuoteRepository;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class QuoteRepositoryImpl implements QuoteRepository {

    private final QuoteMapper quoteMapper;
    private final ObjectMapper objectMapper;

    public QuoteRepositoryImpl(QuoteMapper quoteMapper, ObjectMapper objectMapper) {
        this.quoteMapper = quoteMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(Quote quote) {
        QuoteCondition condition = quote.getCondition();

        QuoteRecord quoteRow = new QuoteRecord(
                quote.getId().value(),
                quote.getQuoteNumber().value(),
                condition.originLocode(),
                condition.destinationLocode(),
                condition.requestedArrivalDate(),
                condition.cargoType().name(),
                condition.weightKg(),
                LocalDateTime.now()
        );
        quoteMapper.insertQuote(quoteRow);

        List<RouteOption> routeOptions = quote.getRouteOptions();
        for (int i = 0; i < routeOptions.size(); i++) {
            RouteOption option = routeOptions.get(i);
            QuoteRouteOptionRecord row = new QuoteRouteOptionRecord(
                    null,
                    quote.getId().value(),
                    option.voyageNumber(),
                    serializeViaLocodes(option.viaLocodes()),
                    option.transitDays(),
                    option.estimatedPrice(),
                    i
            );
            quoteMapper.insertRouteOption(row);
        }
    }

    @Override
    public Optional<Quote> findById(QuoteId id) {
        return quoteMapper.findById(id.value())
                .map(quoteRow -> {
                    List<QuoteRouteOptionRecord> optionRows =
                            quoteMapper.findRouteOptionsByQuoteId(id.value());
                    return toQuote(quoteRow, optionRows);
                });
    }

    @Override
    public List<Quote> findAll() {
        List<QuoteRecord> quoteRows = quoteMapper.findAll();
        if (quoteRows.isEmpty()) {
            return List.of();
        }
        List<UUID> quoteIds = quoteRows.stream().map(QuoteRecord::id).toList();
        Map<UUID, List<QuoteRouteOptionRecord>> optionsByQuoteId =
                quoteMapper.findRouteOptionsByQuoteIds(quoteIds).stream()
                        .collect(Collectors.groupingBy(QuoteRouteOptionRecord::quoteId));

        return quoteRows.stream()
                .map(quoteRow -> toQuote(quoteRow, optionsByQuoteId.getOrDefault(quoteRow.id(), List.of())))
                .toList();
    }

    private Quote toQuote(QuoteRecord row, List<QuoteRouteOptionRecord> optionRows) {
        QuoteId id = new QuoteId(row.id());
        QuoteNumber number = QuoteNumber.of(row.quoteNumber());
        QuoteCondition condition = new QuoteCondition(
                row.originLocode(),
                row.destinationLocode(),
                row.requestedArrivalDate(),
                CargoType.valueOf(row.cargoType()),
                row.weightKg()
        );
        List<RouteOption> routeOptions = optionRows.stream()
                .map(this::toRouteOption)
                .toList();
        return Quote.reconstitute(id, number, condition, routeOptions);
    }

    private RouteOption toRouteOption(QuoteRouteOptionRecord row) {
        List<String> viaLocodes = deserializeViaLocodes(row.viaLocodes());
        return new RouteOption(viaLocodes, row.transitDays(), row.estimatedPrice(), row.voyageNumber());
    }

    private String serializeViaLocodes(List<String> viaLocodes) {
        try {
            return objectMapper.writeValueAsString(viaLocodes);
        } catch (JacksonException e) {
            throw new IllegalStateException("viaLocodes のシリアライズに失敗しました", e);
        }
    }

    private List<String> deserializeViaLocodes(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JacksonException e) {
            throw new IllegalStateException("viaLocodes のデシリアライズに失敗しました", e);
        }
    }
}

