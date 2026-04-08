package com.example.cargotracker.routing.infrastructure.repositories;

import java.time.LocalDateTime;

public class VoyageRecord {

    private Long id;
    private String voyageNumber;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getVoyageNumber() { return voyageNumber; }
    public void setVoyageNumber(String voyageNumber) { this.voyageNumber = voyageNumber; }
}
