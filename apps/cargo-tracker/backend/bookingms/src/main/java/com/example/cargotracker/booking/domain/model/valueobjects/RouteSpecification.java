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

    /**
     * 誤配の再設計として満たすか（US28 §受入基準 4・5・6）。
     *
     * <p><b>出発地は見ない。</b> 再設計は<b>現在地</b>を起点にする——予定ルートを
     * 外れた貨物は、もう出発地には無い。目的地と貨物仕様は元の予約から
     * 引き継ぐので、そちらは変わらず見る。</p>
     *
     * <p><b>期限も見ない。</b> 現在地からでは間に合わないのが普通で、
     * 超過を断ると貨物が動かせなくなる。超過した事実は
     * {@link #overdueDays} が数え、イベントに載せて荷主への説明に使う。</p>
     */
    public boolean isSatisfiedByRedesign(CargoItinerary itinerary) {
        return itinerary != null && destination.equals(itinerary.destination());
    }

    /**
     * 到着期限を何日超えるか（超えないなら 0）。
     *
     * <p><b>日付で比べる</b>（期限は {@code DATE}）。到着時刻と素朴に比べると
     * 期限当日に着く旅程を落とす。日付にするタイムゾーンは業務のものを渡す。</p>
     */
    public int overdueDays(CargoItinerary itinerary, ZoneId zone) {
        LocalDate arrival = LocalDate.ofInstant(itinerary.finalArrival(), zone);
        long days = java.time.temporal.ChronoUnit.DAYS.between(arrivalDeadline, arrival);
        return (int) Math.max(days, 0);
    }
}
