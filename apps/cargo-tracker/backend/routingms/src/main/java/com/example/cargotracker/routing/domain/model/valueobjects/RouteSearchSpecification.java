package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.Location;
import java.time.LocalDate;
import java.util.Set;

/**
 * 経路探索の条件（US08）。
 *
 * <p>条件は予約の経路仕様から組む（{@code cargo_summary}）。<b>画面から組み立てない。</b>
 * 画面が組むと、予約の期限を直したのに古い期限で探すことが起きる。</p>
 *
 * @param origin 予約の出発地
 * @param destination 目的地
 * @param arrivalDeadline 到着期限（日付。時刻は持たない）
 * @param cargoType 貨物種別。これを受け入れない航海は候補に出さない
 * @param excludePorts 通したくない港（US10 の条件調整で使う）
 * @param departFrom 探索の起点。誤配の再設計（IT11）で現在地を渡す。通常は null
 */
public record RouteSearchSpecification(
        Location origin,
        Location destination,
        LocalDate arrivalDeadline,
        CargoType cargoType,
        Set<Location> excludePorts,
        Location departFrom) {

    public RouteSearchSpecification {
        if (origin == null || destination == null) {
            throw new BusinessRuleViolation("出発地と目的地は必須です");
        }
        if (origin.equals(destination)) {
            throw new BusinessRuleViolation(
                    "出発地と目的地が同じです: " + origin.unLocode().value());
        }
        if (arrivalDeadline == null) {
            throw new BusinessRuleViolation("到着期限は必須です");
        }
        if (cargoType == null) {
            throw new BusinessRuleViolation("貨物種別は必須です");
        }
        excludePorts = excludePorts == null ? Set.of() : Set.copyOf(excludePorts);
        if (excludePorts.contains(destination) || excludePorts.contains(origin)
                || (departFrom != null && excludePorts.contains(departFrom))) {
            // 端点を除外すると、必ず 0 件になる条件を黙って受け付けることになる。
            throw new BusinessRuleViolation("出発地・目的地は除外できません");
        }
    }

    /**
     * 探索の起点。
     *
     * <p>{@code departFrom} があればそこから探す（誤配の再設計）。無ければ出発地。
     * 呼ぶ側で {@code departFrom != null ? ... : ...} を書くと、書き忘れた場所だけが
     * 現在地を無視して元の出発地から探す。</p>
     */
    public Location searchOrigin() {
        return departFrom == null ? origin : departFrom;
    }

    /** その港を通ってよいか。 */
    public boolean allows(Location port) {
        return !excludePorts.contains(port);
    }

    /** その航海に載せられるか（受入貨物種別）。 */
    public boolean acceptedBy(Set<CargoType> acceptedCargoTypes) {
        return acceptedCargoTypes.contains(cargoType);
    }
}
