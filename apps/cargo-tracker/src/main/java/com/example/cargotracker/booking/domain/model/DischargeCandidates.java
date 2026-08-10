package com.example.cargotracker.booking.domain.model;

import com.example.cargotracker.shared.domain.model.Location;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 輸送中の貨物を降ろせる場所（US30）。
 *
 * <p>受入基準は「追跡管理者は<strong>陸揚げ地（現在地の港または次の寄港地）</strong>を
 * 指定して承認できる」と定めている。
 *
 * <p><strong>自由入力にしない。</strong> 船が寄らない港を指定できると、
 * 降ろせない場所で降ろす手配をすることになる。
 *
 * <p><strong>過ぎた寄港地を候補にしない。</strong> 旅程の全部を並べると、
 * すでに通り過ぎた港が選べてしまう。<strong>船は戻らない。</strong>
 */
public final class DischargeCandidates {

    private DischargeCandidates() {
    }

    /**
     * 候補を組み立てる。
     *
     * <p><strong>現在地を先頭に置く。</strong> いちばん早く降ろせる場所であり、
     * 追跡管理者が最初に見る選択肢である。
     *
     * <p><strong>現在地が読めなくても候補を空にしない。</strong> 追跡の記録が
     * まだ無い貨物はある。その場合は旅程の残りだけを候補にする —
     * <strong>候補が空だと承認そのものができなくなる</strong>。
     *
     * @param currentLocation いまの場所。<strong>読めなければ {@code null}</strong>
     * @param itinerary       旅程。<strong>割り当て前は {@code null}</strong>
     * @param now             いまの時刻（<strong>過ぎた寄港地を除くために使う</strong>）
     * @return 重複を除いた候補（<strong>順序を保つ</strong>）
     */
    public static List<Location> of(
            Location currentLocation, CargoItinerary itinerary, Instant now) {
        Set<Location> candidates = new LinkedHashSet<>();
        if (currentLocation != null) {
            candidates.add(currentLocation);
        }
        if (itinerary != null) {
            for (Leg leg : itinerary.legs()) {
                // **まだ着いていない揚地だけを候補にする。** 船は戻らない
                if (now == null || leg.unloadTime() == null || leg.unloadTime().isAfter(now)) {
                    candidates.add(leg.unloadLocation());
                }
            }
        }
        return new ArrayList<>(candidates);
    }
}
