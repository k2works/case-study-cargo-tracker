package com.example.cargotracker.booking.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.Location;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 経路の要求（domain-model.md「Cargo 集約の不変条件」2・5）。
 *
 * <p>到着期限は<b>日付</b>で持つ。期限当日に着く便は「間に合う」扱いなので、
 * 時刻付きで持って素朴に比較すると、当日着を誤って落とす。</p>
 */
public record RouteSpecification(Location origin, Location destination, LocalDate arrivalDeadline) {

    public RouteSpecification {
        if (origin == null || destination == null) {
            throw new BusinessRuleViolation("出発地と目的地は必須です");
        }
        if (origin.equals(destination)) {
            throw new BusinessRuleViolation(
                    "出発地と目的地が同じです: " + origin.unLocode());
        }
        if (arrivalDeadline == null) {
            throw new BusinessRuleViolation("到着期限は必須です");
        }
    }

    /**
     * 旅程がこの経路仕様を満たすか（不変条件 5）。
     *
     * <p>起点・終点が一致し、<b>期限までに着く</b>こと。</p>
     *
     * <p><b>期限は日付で比べる。</b> 期限は日付（{@code arrival_deadline DATE}）なので、
     * 到着時刻と素朴に比べると期限当日に着く便を落とす。日付にするタイムゾーンは
     * 業務のものを渡す（UTC で判断すると、時差の分だけ「当日」が動く）。</p>
     *
     * @param zone 業務タイムゾーン。呼ぶ側で {@code ZoneId.systemDefault()} を使わない
     */
    public boolean isSatisfiedBy(CargoItinerary itinerary, ZoneId zone) {
        if (itinerary == null) {
            return false;
        }
        if (!origin.equals(itinerary.origin()) || !destination.equals(itinerary.destination())) {
            return false;
        }
        return !LocalDate.ofInstant(itinerary.finalArrival(), zone).isAfter(arrivalDeadline);
    }
}
