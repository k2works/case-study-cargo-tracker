package com.example.bookingms.domain.model.valueobjects;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 旅程の1区間（Leg）値オブジェクト
 */
public class Leg {

    private final String voyageNumber;
    private final String loadLocationUnlocode;
    private final String unloadLocationUnlocode;
    private final LocalDateTime loadTime;
    private final LocalDateTime unloadTime;

    public Leg(String voyageNumber, String loadLocationUnlocode, String unloadLocationUnlocode,
               LocalDateTime loadTime, LocalDateTime unloadTime) {
        Objects.requireNonNull(voyageNumber, "voyageNumber must not be null");
        Objects.requireNonNull(loadLocationUnlocode, "loadLocationUnlocode must not be null");
        Objects.requireNonNull(unloadLocationUnlocode, "unloadLocationUnlocode must not be null");
        this.voyageNumber = voyageNumber;
        this.loadLocationUnlocode = loadLocationUnlocode;
        this.unloadLocationUnlocode = unloadLocationUnlocode;
        this.loadTime = loadTime;
        this.unloadTime = unloadTime;
    }

    public String getVoyageNumber() {
        return voyageNumber;
    }

    public String getLoadLocationUnlocode() {
        return loadLocationUnlocode;
    }

    public String getUnloadLocationUnlocode() {
        return unloadLocationUnlocode;
    }

    public LocalDateTime getLoadTime() {
        return loadTime;
    }

    public LocalDateTime getUnloadTime() {
        return unloadTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Leg leg = (Leg) o;
        return Objects.equals(voyageNumber, leg.voyageNumber)
                && Objects.equals(loadLocationUnlocode, leg.loadLocationUnlocode)
                && Objects.equals(unloadLocationUnlocode, leg.unloadLocationUnlocode)
                && Objects.equals(loadTime, leg.loadTime)
                && Objects.equals(unloadTime, leg.unloadTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(voyageNumber, loadLocationUnlocode, unloadLocationUnlocode, loadTime, unloadTime);
    }

    @Override
    public String toString() {
        return "Leg{voyageNumber=" + voyageNumber + ", " + loadLocationUnlocode + " -> " + unloadLocationUnlocode + '}';
    }
}
