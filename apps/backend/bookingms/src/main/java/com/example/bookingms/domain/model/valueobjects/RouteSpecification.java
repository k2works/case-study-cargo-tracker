package com.example.bookingms.domain.model.valueobjects;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 経路仕様値オブジェクト
 */
public class RouteSpecification {

    private final String originUnlocode;
    private final String destinationUnlocode;
    private final LocalDate arrivalDeadline;

    public RouteSpecification(String originUnlocode, String destinationUnlocode, LocalDate arrivalDeadline) {
        Objects.requireNonNull(originUnlocode, "originUnlocode must not be null");
        Objects.requireNonNull(destinationUnlocode, "destinationUnlocode must not be null");
        if (originUnlocode.equals(destinationUnlocode)) {
            throw new IllegalArgumentException("origin and destination must be different");
        }
        this.originUnlocode = originUnlocode;
        this.destinationUnlocode = destinationUnlocode;
        this.arrivalDeadline = arrivalDeadline;
    }

    public String getOriginUnlocode() {
        return originUnlocode;
    }

    public String getDestinationUnlocode() {
        return destinationUnlocode;
    }

    public LocalDate getArrivalDeadline() {
        return arrivalDeadline;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RouteSpecification that = (RouteSpecification) o;
        return Objects.equals(originUnlocode, that.originUnlocode)
                && Objects.equals(destinationUnlocode, that.destinationUnlocode)
                && Objects.equals(arrivalDeadline, that.arrivalDeadline);
    }

    @Override
    public int hashCode() {
        return Objects.hash(originUnlocode, destinationUnlocode, arrivalDeadline);
    }
}
