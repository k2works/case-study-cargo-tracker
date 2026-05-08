package com.example.bookingms.domain.model.valueobjects;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 貨物旅程（CargoItinerary）値オブジェクト
 * 経路割当時に Cargo 集約に設定される
 */
public class CargoItinerary {

    private final List<Leg> legs;

    public CargoItinerary(List<Leg> legs) {
        Objects.requireNonNull(legs, "legs must not be null");
        if (legs.isEmpty()) {
            throw new IllegalArgumentException("legs must not be empty");
        }
        this.legs = Collections.unmodifiableList(legs);
    }

    public List<Leg> getLegs() {
        return legs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CargoItinerary that = (CargoItinerary) o;
        return Objects.equals(legs, that.legs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(legs);
    }

    @Override
    public String toString() {
        return "CargoItinerary{legs=" + legs + '}';
    }
}
