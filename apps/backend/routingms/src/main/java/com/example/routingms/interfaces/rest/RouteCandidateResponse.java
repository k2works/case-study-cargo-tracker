package com.example.routingms.interfaces.rest;

import com.example.routingms.domain.model.RouteRecommendation;
import com.example.routingms.domain.model.TransitEdge;
import com.example.routingms.domain.model.TransitPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 経路候補 1 件分の応答（US08・[ADR-017]）。
 *
 * <p>受入基準が求める <strong>所要日数・経由港・費用・航海番号</strong> をすべて返す。
 * 地点は名称も返す（画面に UN/LOCODE の対訳表を持たせない）。
 *
 * @param rank 推奨順の順位。1 が最上位。**画面は並べ替えない**（並べ方は [ADR-018] が持つ）
 * @param estimatedCost 費用の<strong>概算</strong>。請求される金額ではない（US21 で実料金に差し替える）
 */
public record RouteCandidateResponse(
        int rank,
        boolean direct,
        List<String> voyageNumbers,
        Instant departureTime,
        Instant arrivalTime,
        int transitDays,
        int transshipmentCount,
        List<PortResponse> transitPorts,
        BigDecimal estimatedCost,
        List<LegResponse> legs) {

    /** 港。UN/LOCODE と名称を対で返す。 */
    public record PortResponse(String unLocode, String name) {
    }

    /** 区間 1 本分。どの航海で、どこからどこへ、いつ運ぶか。 */
    public record LegResponse(
            String voyageNumber,
            String fromUnLocode,
            String fromName,
            String toUnLocode,
            String toName,
            Instant departureTime,
            Instant arrivalTime) {

        static LegResponse from(TransitEdge edge) {
            return new LegResponse(edge.voyageNumber().value(),
                    edge.from().unLocode(), edge.from().name(),
                    edge.to().unLocode(), edge.to().name(),
                    edge.departureTime(), edge.arrivalTime());
        }
    }

    public static RouteCandidateResponse from(TransitPath path, int rank) {
        return new RouteCandidateResponse(
                rank,
                path.isDirect(),
                path.voyageNumbers().stream().map(number -> number.value()).toList(),
                path.departureTime(),
                path.arrivalTime(),
                path.transitDays(),
                path.transshipmentCount(),
                path.transitPorts().stream()
                        .map(port -> new PortResponse(port.unLocode(), port.name())).toList(),
                RouteRecommendation.estimatedCost(path),
                path.edges().stream().map(LegResponse::from).toList());
    }
}
