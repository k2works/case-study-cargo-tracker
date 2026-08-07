package com.example.cargotracker.booking.infrastructure.repositories;

import java.time.Instant;

/** 確定した旅程の区間 1 本（読み取り）。 */
public class ItineraryLegRow {

    private String voyageNumber;
    private String loadLocation;
    private String unloadLocation;
    private Instant loadTime;
    private Instant unloadTime;

    public String getVoyageNumber() {
        return voyageNumber;
    }

    public void setVoyageNumber(String voyageNumber) {
        this.voyageNumber = voyageNumber;
    }

    public String getLoadLocation() {
        return loadLocation;
    }

    public void setLoadLocation(String loadLocation) {
        this.loadLocation = loadLocation;
    }

    public String getUnloadLocation() {
        return unloadLocation;
    }

    public void setUnloadLocation(String unloadLocation) {
        this.unloadLocation = unloadLocation;
    }

    public Instant getLoadTime() {
        return loadTime;
    }

    public void setLoadTime(Instant loadTime) {
        this.loadTime = loadTime;
    }

    public Instant getUnloadTime() {
        return unloadTime;
    }

    public void setUnloadTime(Instant unloadTime) {
        this.unloadTime = unloadTime;
    }
}
