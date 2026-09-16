package com.example.cargotracker.handling.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.location.Location;
import java.util.List;

/**
 * 貨物の写し（ACL の読み取りモデル / domain-model.md「Handling Context」）。
 *
 * <p><b>予定ルート外かどうかは、ここが 1 か所で答える。</b> 集約も画面もこの判定を
 * 書き直さない。書き直すと判定が 2 つになり、片方だけ直る。</p>
 *
 * <p><b>Booking / Tracking の型を持ち込まない。</b> 港は共有カーネルの
 * {@link Location} で表す。</p>
 */
public record CargoSnapshot(
        String trackingNumber,
        String bookingId,
        Location origin,
        Location destination,
        String cargoType,
        List<LegSnapshot> legs) {

    public CargoSnapshot {
        legs = legs == null ? List.of() : List.copyOf(legs);
    }

    /** 予定の旅程の 1 区間。<b>時刻は持たない</b>（[ADR-0012] 決定 4）。 */
    public record LegSnapshot(String voyageNumber, Location load, Location unload) {
    }

    /**
     * その種別をその場所で行うのは予定外か（不変条件 2・3）。
     *
     * <p>照合する港は種別が決める（domain-model.md「荷役種別ごとの要件」）。</p>
     *
     * <ul>
     *   <li>{@code RECEIVE} … 出発港</li>
     *   <li>{@code LOAD} … 旅程のどこかの積込港</li>
     *   <li>{@code UNLOAD} … 旅程のどこかの荷降港</li>
     *   <li>{@code CLAIM} … 目的港</li>
     * </ul>
     *
     * <p><b>旅程が無いなら予定外に倒す</b>（不変条件 3）。分からないときは
     * 「予定どおり」と言わない——予定外の記録は残るが、予定どおりと記録すると
     * 誤配が見えなくなる。</p>
     */
    public boolean isOffRoute(HandlingType type, Location location) {
        if (location == null) {
            return true;
        }
        return switch (type) {
            case RECEIVE -> !location.equals(origin);
            case CLAIM -> !location.equals(destination);
            case LOAD -> legs.isEmpty()
                    || legs.stream().noneMatch(leg -> location.equals(leg.load()));
            case UNLOAD -> legs.isEmpty()
                    || legs.stream().noneMatch(leg -> location.equals(leg.unload()));
        };
    }
}
