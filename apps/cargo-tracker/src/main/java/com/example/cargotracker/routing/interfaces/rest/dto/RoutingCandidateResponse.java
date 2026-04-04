package com.example.cargotracker.routing.interfaces.rest.dto;

import com.example.cargotracker.routing.domain.model.CargoType;
import com.example.cargotracker.routing.domain.model.RouteCandidate;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * ルート候補レスポンス DTO。
 */
@Schema(description = "ルート候補レスポンス")
public record RoutingCandidateResponse(
        @Schema(description = "航海番号", example = "SG001")
        String voyageNumber,
        @Schema(description = "経由港 UN/LOCODE リスト")
        List<String> viaLocodes,
        @Schema(description = "所要日数", example = "14")
        int transitDays,
        @Schema(description = "概算料金（円）", example = "150000")
        BigDecimal estimatedPrice,
        @Schema(description = "推定到着日", example = "2026-05-28")
        LocalDate estimatedArrival,
        @Schema(description = "推定出発日", example = "2026-05-14")
        LocalDate estimatedDeparture,
        @Schema(description = "対応貨物種別")
        List<String> supportedCargoTypes
) {
    public static RoutingCandidateResponse from(RouteCandidate candidate) {
        return new RoutingCandidateResponse(
                candidate.voyageNumber(),
                candidate.viaLocodes(),
                candidate.transitDays(),
                candidate.estimatedPrice(),
                candidate.estimatedArrival(),
                candidate.estimatedDeparture(),
                candidate.supportedCargoTypes().stream()
                        .map(CargoType::name)
                        .sorted()
                        .toList()
        );
    }
}
