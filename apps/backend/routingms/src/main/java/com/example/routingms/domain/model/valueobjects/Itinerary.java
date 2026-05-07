package com.example.routingms.domain.model.valueobjects;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 旅程（Itinerary）値オブジェクト
 * 出発地から到着地までの輸送区間リスト
 */
public class Itinerary {

    private final List<Leg> legs;

    public Itinerary(List<Leg> legs) {
        if (legs == null || legs.isEmpty()) {
            throw new IllegalArgumentException("Itinerary must have at least one Leg");
        }
        this.legs = List.copyOf(legs);
    }

    public List<Leg> getLegs() {
        return legs;
    }

    /**
     * 旅程の出発地 UNLOCODE を返す
     */
    public String getOriginUnlocode() {
        return legs.get(0).getLoadLocationUnlocode();
    }

    /**
     * 旅程の到着地 UNLOCODE を返す
     */
    public String getDestinationUnlocode() {
        return legs.get(legs.size() - 1).getUnloadLocationUnlocode();
    }

    /**
     * 旅程の最終到着日時を返す
     */
    public ZonedDateTime getFinalUnloadTime() {
        return legs.get(legs.size() - 1).getUnloadTime();
    }

    /**
     * 旅程の所要日数を返す（最初の積載から最後の荷卸しまで）
     */
    public long getDurationDays() {
        ZonedDateTime start = legs.get(0).getLoadTime();
        ZonedDateTime end = getFinalUnloadTime();
        return start.until(end, java.time.temporal.ChronoUnit.DAYS);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Itinerary itinerary = (Itinerary) o;
        return Objects.equals(legs, itinerary.legs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(legs);
    }
}
