package com.example.routingms.interfaces.rest.dto;

import com.example.routingms.domain.projections.RouteDesignRequestProjection;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 経路設計依頼の REST レスポンス（US06 / cross-service、ADR-0009）。
 *
 * <p>bookingms から Kafka 経由で受信し routingms に記録した経路設計依頼
 * （route_design_request read model）を表す。</p>
 */
public record RouteDesignRequestResponse(
        String bookingId,
        String originUnlocode,
        String destinationUnlocode,
        LocalDate arrivalDeadline,
        String cargoType,
        String status,
        LocalDateTime requestedAt) {

    public static RouteDesignRequestResponse from(RouteDesignRequestProjection projection) {
        return new RouteDesignRequestResponse(
                projection.getBookingId(),
                projection.getOriginUnlocode(),
                projection.getDestinationUnlocode(),
                projection.getArrivalDeadline(),
                projection.getCargoType(),
                projection.getStatus(),
                projection.getRequestedAt());
    }
}
