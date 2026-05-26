package com.example.routingms.interfaces.rest.dto;

import com.example.routingms.domain.model.RouteLeg;

import java.time.LocalDateTime;

/**
 * 経路候補を構成する輸送区間の REST レスポンス（US08）。
 */
public record RouteLegResponse(
        String voyageNumber,
        String loadUnlocode,
        String unloadUnlocode,
        LocalDateTime loadTime,
        LocalDateTime unloadTime) {

    public static RouteLegResponse from(RouteLeg leg) {
        return new RouteLegResponse(
                leg.voyageNumber(),
                leg.loadUnlocode(),
                leg.unloadUnlocode(),
                leg.loadTime(),
                leg.unloadTime());
    }
}
