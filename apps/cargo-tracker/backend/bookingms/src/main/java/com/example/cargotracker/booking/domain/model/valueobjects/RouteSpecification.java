package com.example.cargotracker.booking.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.location.Location;
import java.time.LocalDate;

/**
 * 経路の要求（domain-model.md「Cargo 集約の不変条件」2・5）。
 *
 * <p>到着期限は<b>日付</b>で持つ。期限当日に着く便は「間に合う」扱いなので、
 * 時刻付きで持って素朴に比較すると、当日着を誤って落とす。</p>
 */
public record RouteSpecification(Location origin, Location destination, LocalDate arrivalDeadline) {

    public RouteSpecification {
        if (origin == null || destination == null) {
            throw new IllegalArgumentException("出発地と目的地は必須です");
        }
        if (origin.equals(destination)) {
            throw new IllegalArgumentException(
                    "出発地と目的地が同じです: " + origin.unLocode());
        }
        if (arrivalDeadline == null) {
            throw new IllegalArgumentException("到着期限は必須です");
        }
    }
}
