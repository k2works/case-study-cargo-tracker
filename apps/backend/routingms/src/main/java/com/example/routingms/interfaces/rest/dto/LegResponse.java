package com.example.routingms.interfaces.rest.dto;

import com.example.routingms.domain.model.valueobjects.Leg;

import java.time.ZonedDateTime;

/**
 * 輸送区間レスポンス DTO
 */
public record LegResponse(
        String voyageNumber,
        String loadLocationUnlocode,
        String unloadLocationUnlocode,
        ZonedDateTime loadTime,
        ZonedDateTime unloadTime
) {
    public static LegResponse from(Leg leg) {
        return new LegResponse(
                leg.getVoyageNumber(),
                leg.getLoadLocationUnlocode(),
                leg.getUnloadLocationUnlocode(),
                leg.getLoadTime(),
                leg.getUnloadTime());
    }
}
