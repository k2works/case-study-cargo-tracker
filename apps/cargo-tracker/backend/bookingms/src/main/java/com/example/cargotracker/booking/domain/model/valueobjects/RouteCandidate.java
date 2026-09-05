package com.example.cargotracker.booking.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import java.util.List;

/**
 * 経路候補 1 件（US08）。<b>予約側から見た候補</b>。
 *
 * <p>routingms の {@code TransitPath} を写した形だが別の型である
 * （domain-model.md「BC ごとに別の型」）。あちらは探索の結果、こちらは
 * 「経路設計者が選ぶ選択肢」で、選んだあとは {@link CargoItinerary} になる。</p>
 *
 * <p><b>候補 ID を持たない。</b> 候補はテーブルに持たないので、選んで送るまでの間に
 * 航海が更新されうる。選んだ内容そのもの（区間の列）を送る。</p>
 *
 * @param legs 区間。順序が業務の意味を持つ
 * @param transitDays 所要日数
 * @param direct 直行便か
 */
public record RouteCandidate(List<Leg> legs, int transitDays, boolean direct) {

    public RouteCandidate {
        if (legs == null || legs.isEmpty()) {
            throw new BusinessRuleViolation("経路候補は 1 区間以上が必要です");
        }
        legs = List.copyOf(legs);
    }
}
