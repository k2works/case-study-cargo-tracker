package com.example.cargotracker.booking.domain.model.valueobjects;
import com.example.cargotracker.booking.domain.model.entities.Leg;

import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import java.time.Instant;
import java.util.List;

/**
 * 旅程。貨物の輸送経路全体（US09 / US11）。
 *
 * <p><strong>守るのは区間をまたぐ 2 つの制約である。</strong> 連結（区間 n の荷降港 =
 * 区間 n+1 の積込港）と時系列（着く前に次の区間は始まらない）。
 * <strong>どちらも 1 行で完結せず行をまたぐため、DB の CHECK 制約では守れない。</strong>
 * `Schedule`（Routing / IT3）と同じ理由で集約側が守る。
 *
 * <p>端点と到着時刻は<strong>区間から導く。保持しない。</strong> 同じ事実を 2 か所に
 * 持つと、区間を足したときに端点だけ古いままになる。
 *
 * @param legs 輸送区間（1 つ以上）
 */
public record CargoItinerary(List<Leg> legs) {

    public CargoItinerary {
        if (legs == null || legs.isEmpty()) {
            throw new IllegalArgumentException("旅程には運送区間が 1 つ以上必要です");
        }
        legs = List.copyOf(legs);
        validate(legs);
    }

    public static CargoItinerary of(List<Leg> legs) {
        return new CargoItinerary(legs);
    }

    private static void validate(List<Leg> legs) {
        for (int i = 1; i < legs.size(); i++) {
            Leg previous = legs.get(i - 1);
            Leg current = legs.get(i);
            if (!previous.unloadLocation().equals(current.loadLocation())) {
                throw new IllegalArgumentException(
                        "旅程がつながっていません: %s で降ろした後 %s から積み込んでいます"
                                .formatted(previous.unloadLocation().unlocode(),
                                        current.loadLocation().unlocode()));
            }
            if (current.loadTime().isBefore(previous.unloadTime())) {
                throw new IllegalArgumentException(
                        "前の区間の荷降より前に積み込んでいます: %s → %s"
                                .formatted(previous.unloadTime(), current.loadTime()));
            }
        }
    }

    /** 出発地（最初の区間の積込港）。 */
    public Location origin() {
        return legs.getFirst().loadLocation();
    }

    /** 目的地（最後の区間の荷降港）。 */
    public Location destination() {
        return legs.getLast().unloadLocation();
    }

    /** 到着予定時刻（最後の区間の荷降日時）。 */
    public Instant arrivalTime() {
        return legs.getLast().unloadTime();
    }

    /** 直行か。区間が 1 つなら乗り継ぎが無い。 */
    public boolean isDirect() {
        return legs.size() == 1;
    }
}
