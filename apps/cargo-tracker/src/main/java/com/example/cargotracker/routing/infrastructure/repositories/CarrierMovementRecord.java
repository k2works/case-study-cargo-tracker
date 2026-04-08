package com.example.cargotracker.routing.infrastructure.repositories;

import java.time.LocalDateTime;

public class CarrierMovementRecord {

    private Long id;
    private Long voyageId;
    private String departureLocationUnlocode;
    private String arrivalLocationUnlocode;
    private LocalDateTime departureDate;
    private LocalDateTime arrivalDate;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getVoyageId() { return voyageId; }
    public void setVoyageId(Long voyageId) { this.voyageId = voyageId; }

    public String getDepartureLocationUnlocode() { return departureLocationUnlocode; }
    public void setDepartureLocationUnlocode(String departureLocationUnlocode) {
        this.departureLocationUnlocode = departureLocationUnlocode;
    }

    public String getArrivalLocationUnlocode() { return arrivalLocationUnlocode; }
    public void setArrivalLocationUnlocode(String arrivalLocationUnlocode) {
        this.arrivalLocationUnlocode = arrivalLocationUnlocode;
    }

    public LocalDateTime getDepartureDate() { return departureDate; }
    public void setDepartureDate(LocalDateTime departureDate) { this.departureDate = departureDate; }

    public LocalDateTime getArrivalDate() { return arrivalDate; }
    public void setArrivalDate(LocalDateTime arrivalDate) { this.arrivalDate = arrivalDate; }
}
