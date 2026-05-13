package com.example.cargotracker.routingms.domain.model.valueobjects;

/**
 * 航海のライフサイクル状態。data-model.md routing_read_db.voyage.status と整合。
 */
public enum VoyageStatus {
    SCHEDULED,
    DEPARTED,
    ARRIVED,
    CANCELLED
}
