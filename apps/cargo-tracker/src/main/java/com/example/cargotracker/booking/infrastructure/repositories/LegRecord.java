package com.example.cargotracker.booking.infrastructure.repositories;

import java.time.Instant;

/** 旅程の区間 1 本の行。 */
public class LegRecord {

    private Long cargoId;
    private String voyageNumber;
    private String loadLocationUnlocode;
    private String unloadLocationUnlocode;
    private Instant loadTime;
    private Instant unloadTime;
    private int seqNumber;

    public Long getCargoId() {
        return cargoId;
    }

    public void setCargoId(Long cargoId) {
        this.cargoId = cargoId;
    }

    public String getVoyageNumber() {
        return voyageNumber;
    }

    public void setVoyageNumber(String voyageNumber) {
        this.voyageNumber = voyageNumber;
    }

    public String getLoadLocationUnlocode() {
        return loadLocationUnlocode;
    }

    public void setLoadLocationUnlocode(String loadLocationUnlocode) {
        this.loadLocationUnlocode = loadLocationUnlocode;
    }

    public String getUnloadLocationUnlocode() {
        return unloadLocationUnlocode;
    }

    public void setUnloadLocationUnlocode(String unloadLocationUnlocode) {
        this.unloadLocationUnlocode = unloadLocationUnlocode;
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

    public int getSeqNumber() {
        return seqNumber;
    }

    public void setSeqNumber(int seqNumber) {
        this.seqNumber = seqNumber;
    }
}
