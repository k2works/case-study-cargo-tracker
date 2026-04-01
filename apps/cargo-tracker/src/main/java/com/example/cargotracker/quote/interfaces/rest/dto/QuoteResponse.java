package com.example.cargotracker.quote.interfaces.rest.dto;

import com.example.cargotracker.quote.domain.model.aggregates.Quote;
import com.example.cargotracker.quote.domain.model.valueobjects.RouteOption;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 見積レスポンス DTO。
 */
@Schema(description = "見積レスポンス")
public record QuoteResponse(
        @Schema(description = "見積 ID (UUID)")
        String id,
        @Schema(description = "見積番号")
        String quoteNumber,
        @Schema(description = "見積条件")
        QuoteConditionDto condition,
        @Schema(description = "ルート候補一覧")
        List<RouteOptionDto> routeOptions
) {
    @Schema(description = "見積条件 DTO")
    public record QuoteConditionDto(
            @Schema(description = "出発地 UN/LOCODE", example = "JPTYO")
            String originLocode,
            @Schema(description = "目的地 UN/LOCODE", example = "USNYC")
            String destinationLocode,
            @Schema(description = "希望着日", example = "2025-12-01")
            LocalDate requestedArrivalDate,
            @Schema(description = "貨物種別 (enum name)", example = "GENERAL_CARGO")
            String cargoType,
            @Schema(description = "貨物種別 表示名", example = "一般貨物")
            String cargoTypeDisplayName,
            @Schema(description = "重量 (kg)", example = "1000.0")
            BigDecimal weightKg
    ) {
    }

    @Schema(description = "ルート候補 DTO")
    public record RouteOptionDto(
            @Schema(description = "経由港 UN/LOCODE リスト")
            List<String> viaLocodes,
            @Schema(description = "所要日数", example = "14")
            int transitDays,
            @Schema(description = "概算料金（円）", example = "150000")
            BigDecimal estimatedPrice,
            @Schema(description = "航海番号", example = "SG001")
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
