package com.example.cargotracker.quote.interfaces.rest.dto;

import com.example.cargotracker.quote.domain.model.aggregates.Quote;
import com.example.cargotracker.quote.domain.model.valueobjects.RouteOption;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 見積レスポンス DTO。
 */
public record QuoteResponse(
        String id,
        String quoteNumber,
        QuoteConditionDto condition,
        List<RouteOptionDto> routeOptions
) {
    public record QuoteConditionDto(
            String originLocode,
            String destinationLocode,
            LocalDate requestedArrivalDate,
            String cargoType,
            String cargoTypeDisplayName,
            BigDecimal weightKg
    ) {
    }

    public record RouteOptionDto(
            List<String> viaLocodes,
            int transitDays,
            BigDecimal estimatedPrice,
            String voyageNumber
    ) {
    }

    public static QuoteResponse from(Quote quote) {
        QuoteConditionDto conditionDto = new QuoteConditionDto(
                quote.getCondition().originLocode(),
                quote.getCondition().destinationLocode(),
                quote.getCondition().requestedArrivalDate(),
                quote.getCondition().cargoType().name(),
                quote.getCondition().cargoType().getDisplayName(),
                quote.getCondition().weightKg()
        );
        List<RouteOptionDto> routeOptionDtos = quote.getRouteOptions().stream()
                .map(QuoteResponse::toRouteOptionDto)
                .toList();
        return new QuoteResponse(
                quote.getId().value().toString(),
                quote.getQuoteNumber().value(),
                conditionDto,
                routeOptionDtos
        );
    }

    private static RouteOptionDto toRouteOptionDto(RouteOption option) {
        return new RouteOptionDto(
                option.viaLocodes(),
                option.transitDays(),
                option.estimatedPrice(),
                option.voyageNumber()
        );
    }
}
