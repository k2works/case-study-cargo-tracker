package com.example.bookingms.interfaces.rest.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 経路割当リクエスト DTO
 */
public record AssignRouteRequest(List<LegRequest> legs) {

    /**
     * 旅程区間リクエスト DTO
     */
    public record LegRequest(
            String voyageNumber,
            String loadLocationUnlocode,
            String unloadLocationUnlocode,
            LocalDateTime loadTime,
            LocalDateTime unloadTime
    ) {}
}
