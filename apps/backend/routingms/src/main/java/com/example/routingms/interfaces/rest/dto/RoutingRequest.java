package com.example.routingms.interfaces.rest.dto;

import java.time.LocalDate;

/**
 * 経路候補算出リクエスト DTO
 */
public record RoutingRequest(
        String originUnlocode,
        String destinationUnlocode,
        LocalDate arrivalDeadline
) {
}
