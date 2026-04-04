package com.example.cargotracker.routing.interfaces.rest.dto;

import com.example.cargotracker.routing.domain.model.RouteDesignCondition;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 経路設計条件レスポンス DTO。
 */
@Schema(description = "経路設計条件レスポンス")
public record RouteDesignConditionResponse(
        @Schema(description = "予約 ID")
        UUID bookingId,
        @Schema(description = "出発地 UN/LOCODE", example = "JPTYO")
        String originLocode,
        @Schema(description = "目的地 UN/LOCODE", example = "SGSIN")
        String destinationLocode,
        @Schema(description = "希望着日", example = "2026-06-30")
        LocalDate requestedArrivalDate,
        @Schema(description = "貨物種別", example = "GENERAL")
        String cargoType,
        @Schema(description = "重量（kg）", example = "500.0")
        BigDecimal weightKg,
        @Schema(description = "条件が揃っているか")
        boolean complete
) {
    public static RouteDesignConditionResponse from(RouteDesignCondition condition) {
        return new RouteDesignConditionResponse(
                condition.bookingId(),
                condition.originLocode(),
                condition.destinationLocode(),
                condition.requestedArrivalDate(),
                condition.cargoType() != null ? condition.cargoType().name() : null,
                condition.weightKg(),
                condition.isComplete()
        );
    }
}
