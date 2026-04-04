package com.example.cargotracker.routing.interfaces.rest.dto;

import java.time.LocalDate;

/**
 * 航海区間詳細 REST レスポンス DTO（US22 #assignModal 表示用）。
 */
public record VoyageLegDetailResponse(
        String originLocode,
        String destinationLocode,
        LocalDate departureDate,
        LocalDate arrivalDate,
        int legOrder
) {}
