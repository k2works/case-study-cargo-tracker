package com.example.cargotracker.booking.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import java.util.List;

/**
 * 見積のルート候補 1 件（US01 §受入基準 3）。
 *
 * <p>{@link RouteCandidate}（経路設計が選ぶ選択肢）に<b>概算料金を付けたもの</b>
 * である。型を分けるのは、候補が「選べるもの」なのに対し、これは「荷主に示した
 * 金額つきの案」だからで、<b>見積として保存される</b>（候補は保存しない）。</p>
 *
 * <p><b>候補 ID を持たない。</b> 保存するのは見積の中の並びで、選ぶのは予約の
 * ときである。ID を持たせても、その間に航海が更新されれば指す先は変わる。</p>
 *
 * @param legs 区間。<b>順序が業務の意味を持つ</b>（地域係数は区間ごとに数える）
 * @param transitDays 所要日数
 * @param estimatedCharge 概算料金（税は載せない）
 * @param overdueDays 希望期限からの超過日数。0 なら間に合う
 */
public record QuotedRoute(
        List<Leg> legs,
        int transitDays,
        EstimatedAmount estimatedCharge,
        int overdueDays) {

    public QuotedRoute {
        if (legs == null || legs.isEmpty()) {
            throw new BusinessRuleViolation("ルート候補は 1 区間以上が必要です");
        }
        if (estimatedCharge == null) {
            throw new BusinessRuleViolation("概算料金は必須です");
        }
        if (overdueDays < 0) {
            throw new BusinessRuleViolation("超過日数は 0 以上です: " + overdueDays);
        }
        legs = List.copyOf(legs);
    }

    /** 希望期限に間に合うか。<b>候補が答える</b>（画面に数え直させない）。 */
    public boolean meetsDeadline() {
        return overdueDays == 0;
    }

    /**
     * 画面と保存に出す航海番号の並び。
     *
     * <p>正典の {@code quotation_candidate} は区間を {@code voyage_numbers
     * VARCHAR(200)} で持つ（経路そのものは予約のとき {@code cargo_leg} に写す）。
     * <b>区切りの規則をここ 1 か所に置く</b>——投影と画面で別々に組み立てると、
     * 片方だけが区切り文字を変える。</p>
     */
    public String voyageNumbers() {
        return String.join(" > ", legs.stream().map(Leg::voyageNumber).toList());
    }

    /** 経由港（積地の並び + 最後の揚地）。<b>荷主が読むのはこれ。</b> */
    public List<String> ports() {
        List<String> ports = new java.util.ArrayList<>();
        for (Leg leg : legs) {
            ports.add(leg.load().unLocode().value());
        }
        ports.add(legs.get(legs.size() - 1).unload().unLocode().value());
        return List.copyOf(ports);
    }
}
