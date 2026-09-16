package com.example.cargotracker.booking.domain.service;

import com.example.cargotracker.shared.domain.location.Location;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * キャンセル承認で指定できる陸揚げ地（US30 §受入基準 5 / 不変条件 9-2）。
 *
 * <p><b>判定を 2 か所に書かない。</b> 集約（{@code Cargo#approveCancellation}）が
 * 断る条件と、画面が出す選択肢は<b>同じもの</b>である。別々に書くと、画面に出て
 * いるのに押すと断られる港が生まれる——その形は IT5 のレビューで一度出ている。</p>
 *
 * <p><b>ドメインサービスに置く。</b> 集約は自分の旅程から、読み口は投影の
 * {@code cargo_leg} と {@code last_handling_unlocode} から、<b>同じ関数</b>を呼ぶ。</p>
 */
public final class DischargeCandidates {

    private DischargeCandidates() {
    }

    /**
     * 区間の 1 つ（積み港と荷降し港だけを見る）。
     *
     * <p>集約の {@code CargoRoutedEvent.Leg} と投影の {@code CargoLegRow} は別の型
     * なので、<b>この判定が要る分だけ</b>を受け取る形にする。</p>
     */
    public record Leg(String loadUnLocode, String unloadUnLocode) {
    }

    /**
     * 指定できる港（現在地 + これから通る荷降し港）。
     *
     * <p><b>「通過済み」は荷降しの済んだ港だけ。</b> 積み港に居ることは、その区間を
     * 通ったことではない——東京で受領した貨物にとって東京 → シンガポールはまだ先で、
     * 積み港も通過済みと数えると<b>次の寄港地が候補から消える</b>（IT15 で実測）。</p>
     *
     * <p><b>現在地が旅程に無い（誤配）ときは全部の荷降し港を候補にする。</b>
     * どこまで進んだか分からない状態で候補を狭めると、実際に降ろせる港まで消える。</p>
     *
     * @param currentUnLocode 現在地。<b>まだ荷役が無ければ {@code null}</b>
     */
    public static List<Location> of(List<Leg> legs, String currentUnLocode) {
        int passed = -1;
        for (int i = 0; i < legs.size(); i++) {
            if (legs.get(i).unloadUnLocode().equals(currentUnLocode)) {
                passed = i;
            }
        }
        // **現在地を先頭に置く。** 追跡管理者がまず考えるのは「いま降ろせるか」で、
        // 一覧の先頭がその答えになる。
        Set<Location> candidates = new LinkedHashSet<>();
        if (currentUnLocode != null) {
            candidates.add(Location.of(currentUnLocode));
        }
        legs.stream().skip(passed + 1L)
                .map(leg -> Location.of(leg.unloadUnLocode()))
                .forEach(candidates::add);
        return List.copyOf(candidates);
    }
}
