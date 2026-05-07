package com.example.routingms.interfaces.rest.dto;

import com.example.routingms.domain.model.valueobjects.Itinerary;

import java.util.List;

/**
 * 旅程レスポンス DTO
 */
public record ItineraryResponse(
        List<LegResponse> legs
) {
    public static ItineraryResponse from(Itinerary itinerary) {
        List<LegResponse> legs = itinerary.getLegs().stream()
                .map(LegResponse::from)
                .toList();
        return new ItineraryResponse(legs);
    }
}
