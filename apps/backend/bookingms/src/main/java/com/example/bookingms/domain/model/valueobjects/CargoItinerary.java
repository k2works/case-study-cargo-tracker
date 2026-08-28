package com.example.bookingms.domain.model.valueobjects;

import com.example.shared.domain.model.Location;
import java.time.Instant;
import java.util.List;

/**
 * 旅程。予約に割り当てられた、積み替えを含む全区間（US09）。
 *
 * <p>Routing Context の {@code TransitPath} と<strong>同じ不変条件を、別の型として持つ</strong>。
 * あちらは都度算出して捨てる探索結果であり、こちらは予約に紐付いて残る記録である。共有すると、
 * 探索の都合（推奨順・費用の概算）が予約側の記録に混ざる。
 */
public final class CargoItinerary {

    private final List<Leg> legs;

    private CargoItinerary(List<Leg> legs) {
        this.legs = List.copyOf(legs);
    }

    /**
     * 新規に組み立てる。ここでだけ検査する。
     *
     * <p>つながっていない旅程を保存すると、荷役の担当者は来ない貨物を待つことになる。
     */
    public static CargoItinerary of(List<Leg> legs) {
        if (legs == null || legs.isEmpty()) {
            throw new IllegalArgumentException("旅程には少なくとも 1 つの区間が必要です");
        }
        for (int i = 1; i < legs.size(); i++) {
            Leg previous = legs.get(i - 1);
            Leg current = legs.get(i);
            if (!previous.unloadLocation().equals(current.loadLocation())) {
                throw new IllegalArgumentException(
                        "区間がつながっていません。前の区間の荷降し地から次の区間が積み込むようにしてください");
            }
            if (current.loadTime().isBefore(previous.unloadTime())) {
                throw new IllegalArgumentException("次の区間の積込は前の区間の荷降しより後にしてください");
            }
        }
        return new CargoItinerary(legs);
    }

    /**
     * 永続化された行から復元する。ここでは検査しない。
     *
     * <p>連結の規則が無かったころの行が読めなくなると、一覧そのものが開けなくなる。
     */
    public static CargoItinerary restore(List<Leg> legs) {
        return new CargoItinerary(legs);
    }

    public List<Leg> legs() {
        return legs;
    }

    public Location origin() {
        return legs.get(0).loadLocation();
    }

    public Location destination() {
        return legs.get(legs.size() - 1).unloadLocation();
    }

    /** 出発予定。最初の区間の積込時刻。 */
    public Instant expectedDepartureTime() {
        return legs.get(0).loadTime();
    }

    /** 到着予定。最後の区間の荷降し時刻。 */
    public Instant expectedArrivalTime() {
        return legs.get(legs.size() - 1).unloadTime();
    }

    /**
     * その地点を通る予定か。
     *
     * <p>誤配の判定（US28）は「そこを通る予定だったか」を旅程に聞く。荷役の場所と突き合わせる
     * のを呼ぶ側の仕事にすると、入口ごとに別の判定が書かれる。
     */
    public boolean includesLocation(Location location) {
        return legs.stream().anyMatch(leg -> leg.loadLocation().equals(location)
                || leg.unloadLocation().equals(location));
    }

    /** 積み替えの回数。区間の数から 1 を引いたもの。 */
    public int transshipmentCount() {
        return legs.size() - 1;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CargoItinerary itinerary && legs.equals(itinerary.legs);
    }

    @Override
    public int hashCode() {
        return legs.hashCode();
    }

    @Override
    public String toString() {
        return "%s → %s（%d 区間）".formatted(origin().unLocode(), destination().unLocode(),
                legs.size());
    }
}
