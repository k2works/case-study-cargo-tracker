package com.example.cargotracker.booking.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.Location;
import java.time.Instant;
import java.util.List;

/**
 * 確定した旅程（US09。domain-model.md「Cargo 集約の不変条件」4）。
 *
 * <p><b>連結と時刻の昇順はここで守る。</b> 経路探索も同じ検査をするが、探索が作った
 * ものだけが旅程になるとは限らない（誤配の再設計・API を直接叩く経路）。
 * 「候補は探索が作ったのだから正しい」としない。</p>
 */
public record CargoItinerary(List<Leg> legs) {

    public CargoItinerary {
        if (legs == null || legs.isEmpty()) {
            throw new BusinessRuleViolation("旅程は 1 区間以上が必要です");
        }
        legs = List.copyOf(legs);
        for (int i = 1; i < legs.size(); i++) {
            Leg previous = legs.get(i - 1);
            Leg current = legs.get(i);
            if (!previous.unload().equals(current.load())) {
                throw new BusinessRuleViolation("区間が連結していません: "
                        + previous.unload().unLocode().value() + " → "
                        + current.load().unLocode().value());
            }
            if (current.loadTime().isBefore(previous.unloadTime())) {
                throw new BusinessRuleViolation("前の区間の到着より前に出発する区間があります: "
                        + previous.unloadTime() + " → " + current.loadTime());
            }
        }
    }

    /** 最初の積地。 */
    public Location origin() {
        return legs.get(0).load();
    }

    /** 最後の揚地。 */
    public Location destination() {
        return legs.get(legs.size() - 1).unload();
    }

    /** 最後の到着日時。期限を満たすかの判断に使う。 */
    public Instant finalArrival() {
        return legs.get(legs.size() - 1).unloadTime();
    }
}
