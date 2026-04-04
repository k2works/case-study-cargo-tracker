package com.example.cargotracker.routing.interfaces.rest.dto;

import com.example.cargotracker.routing.domain.model.Voyage;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 航海スケジュール REST レスポンス DTO。
 */
public record VoyageScheduleResponse(
    String voyageNumber,
    String carrierName,
    List<String> supportedCargoTypes,
    List<VoyageLegResponse> legs
) {

    public record VoyageLegResponse(
        String originLocode,
        String destinationLocode,
        String departureDate,
        String arrivalDate,
        int transitDays
    ) {}

    public static VoyageScheduleResponse from(Voyage voyage) {
        List<String> cargoTypes = voyage.supportedCargoTypes().stream()
            .map(Enum::name)
            .sorted()
            .collect(Collectors.toList());

        List<VoyageLegResponse> legResponses = voyage.legs().stream()
            .map(l -> new VoyageLegResponse(
                l.originLocode(),
                l.destinationLocode(),
                l.departureDate().toString(),
                l.arrivalDate().toString(),
                l.transitDays()
            ))
            .toList();

        return new VoyageScheduleResponse(
            voyage.voyageNumber(),
            voyage.carrierName(),
            cargoTypes,
            legResponses
        );
    }
}
