package com.example.cargotracker.booking.infrastructure.repositories;

import java.time.Instant;

/** 確定した旅程の区間 1 本（読み取り）。 */
public class ItineraryLegRow {

    private String voyageNumber;
    private String loadLocation;
    private String unloadLocation;
    private Instant loadTime;
    private Instant unloadTime;

    /** 現在の航海スケジュール上の発着（C9）。便が消えていれば null。 */
    private Instant currentLoadTime;
    private Instant currentUnloadTime;

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

    public Instant getCurrentLoadTime() {
        return currentLoadTime;
    }

    public void setCurrentLoadTime(Instant currentLoadTime) {
        this.currentLoadTime = currentLoadTime;
    }

    public Instant getCurrentUnloadTime() {
        return currentUnloadTime;
    }

    public void setCurrentUnloadTime(Instant currentUnloadTime) {
        this.currentUnloadTime = currentUnloadTime;
    }
}
