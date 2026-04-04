package com.example.cargotracker.routing.infrastructure.repositories;

/**
 * 航海（voyages）の DB レコード。
 */
public record VoyageRecord(
    String voyageNumber,
    String carrierName,
    String supportedCargoTypes
) {}
