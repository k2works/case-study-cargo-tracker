package com.example.bookingms.domain.model;

import java.math.BigDecimal;

/**
 * ルート候補（US01-3）。
 *
 * <p><strong>4 項目を持つ</strong>——経由港・所要日数・概算料金・航海番号。
 * 受入基準 01-3 が「候補ごとに表示される」と定めており、<strong>1 つ欠けても
 * 字面は満たす</strong>（IT11 Try 2）。
 *
 * <p><strong>旅程（{@link CargoItinerary}）とは別の型である。</strong>あちらは
 * 割り当てが確定した経路であり、こちらは荷主に見せる候補である。見積の候補を
 * 旅程で持つと、確定していないものが確定した経路として扱われうる。
 *
 * <p><strong>概算料金は billingms が計算する</strong>（[ADR-028] 決定 6）。
 * 式を 2 つ持たない——持てば必ずずれ、荷主に出した見積と請求が違う金額になる。
 *
 * @param voyageNumber 航海番号
 * @param transitPort 経由港（UN/LOCODE）。<strong>直行なら {@code null}</strong>
 * @param transitDays 所要日数
 * @param estimatedCost 概算料金
 */
public record RouteCandidate(String voyageNumber, String transitPort, int transitDays,
        BigDecimal estimatedCost) {

    public RouteCandidate {
        if (voyageNumber == null || voyageNumber.isBlank()) {
            throw new IllegalArgumentException("航海番号を指定してください");
        }
        if (transitDays < 0) {
            throw new IllegalArgumentException("所要日数が負です: " + transitDays);
        }
        if (estimatedCost == null || estimatedCost.signum() < 0) {
            throw new IllegalArgumentException("概算料金が負です: " + estimatedCost);
        }
        if (transitPort != null && transitPort.isBlank()) {
            transitPort = null;
        }
    }

    /** 直行か（経由港を持たない）。 */
    public boolean direct() {
        return transitPort == null;
    }
}
