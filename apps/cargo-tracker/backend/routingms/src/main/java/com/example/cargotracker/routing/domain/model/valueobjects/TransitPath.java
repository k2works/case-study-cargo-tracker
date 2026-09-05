package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.Location;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 経路候補 1 件（US08）。区間の列。
 *
 * <p><b>連結と時刻の昇順はここで守る。</b> 探索がそれを組み立てるが、検査を探索に
 * 置くと、別の作り方（再設計・画面からの手組み）で作られた経路が素通りする。</p>
 */
public record TransitPath(List<TransitEdge> edges) {

    public TransitPath {
        if (edges == null || edges.isEmpty()) {
            throw new BusinessRuleViolation("経路は 1 区間以上が必要です");
        }
        edges = List.copyOf(edges);
        for (int i = 1; i < edges.size(); i++) {
            TransitEdge previous = edges.get(i - 1);
            TransitEdge current = edges.get(i);
            if (!previous.unload().equals(current.load())) {
                throw new BusinessRuleViolation("区間が連結していません: "
                        + previous.unload().unLocode().value() + " → "
                        + current.load().unLocode().value());
            }
            if (current.loadTime().isBefore(previous.unloadTime())) {
                // 前の便が着く前に出る便には乗れない。
                throw new BusinessRuleViolation("前の区間の到着より前に出発する区間があります: "
                        + previous.unloadTime() + " → " + current.loadTime());
            }
        }
    }

    public Location origin() {
        return edges.get(0).load();
    }

    public Location destination() {
        return edges.get(edges.size() - 1).unload();
    }

    /**
     * 最初の出発から最後の到着まで。
     *
     * <p><b>区間の合計にしない。</b> 港で乗り継ぎを待つあいだも荷主は待っている。
     * 合計にすると、待ちの長い経路が短く見える。</p>
     */
    public Duration totalDuration() {
        return Duration.between(edges.get(0).loadTime(),
                edges.get(edges.size() - 1).unloadTime());
    }

    /** 直行便か（受入基準 5）。 */
    public boolean isDirect() {
        return edges.size() == 1;
    }

    /** 経由港（受入基準 3）。端点は含まない。 */
    public List<Location> viaPorts() {
        return edges.stream().limit(edges.size() - 1L).map(TransitEdge::unload).toList();
    }

    /**
     * 期限を何日超えるか（超えないなら 0）。
     *
     * <p><b>日付で比べる。</b> 期限は日付（{@code arrival_deadline DATE}）なので、
     * 到着時刻と素朴に比べると期限当日に着く便を落とす。日付にするタイムゾーンは
     * 業務のものを渡す（UTC で判断すると、時差の分だけ「当日」が動く）。</p>
     */
    public int overdueDays(RouteSearchSpecification specification, ZoneId zone) {
        LocalDate arrival = LocalDate.ofInstant(
                edges.get(edges.size() - 1).unloadTime(), zone);
        long days = ChronoUnit.DAYS.between(specification.arrivalDeadline(), arrival);
        return (int) Math.max(days, 0);
    }

    /** 期限に間に合うか。 */
    public boolean meetsDeadline(RouteSearchSpecification specification, ZoneId zone) {
        return overdueDays(specification, zone) == 0;
    }
}
