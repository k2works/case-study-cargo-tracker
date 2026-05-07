package com.example.routingms.domain.model.valueobjects;

import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * 輸送区間（Leg）値オブジェクト
 * 航海の1区間を表す
 */
public class Leg {

    private final String voyageNumber;
    private final String loadLocationUnlocode;
    private final String unloadLocationUnlocode;
    private final ZonedDateTime loadTime;
    private final ZonedDateTime unloadTime;

    public Leg(String voyageNumber, String loadLocationUnlocode, String unloadLocationUnlocode,
               ZonedDateTime loadTime, ZonedDateTime unloadTime) {
        Objects.requireNonNull(voyageNumber, "voyageNumber must not be null");
        Objects.requireNonNull(loadLocationUnlocode, "loadLocationUnlocode must not be null");
        Objects.requireNonNull(unloadLocationUnlocode, "unloadLocationUnlocode must not be null");
        Objects.requireNonNull(loadTime, "loadTime must not be null");
        Objects.requireNonNull(unloadTime, "unloadTime must not be null");
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

    public ZonedDateTime getLoadTime() {
        return loadTime;
    }

    public ZonedDateTime getUnloadTime() {
        return unloadTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Leg leg = (Leg) o;
        return Objects.equals(voyageNumber, leg.voyageNumber)
                && Objects.equals(loadLocationUnlocode, leg.loadLocationUnlocode)
                && Objects.equals(unloadLocationUnlocode, leg.unloadLocationUnlocode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(voyageNumber, loadLocationUnlocode, unloadLocationUnlocode);
    }
}
