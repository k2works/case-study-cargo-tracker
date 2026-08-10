package com.example.cargotracker.billing.domain.model;

import java.math.BigDecimal;

/**
 * 基本料金を算出するドメインサービス（US21）。
 *
 * <p><strong>基本料金 = 距離係数 × 重量（kg） × 貨物種別係数</strong>
 * （{@code domain-model.md}「金額の丸め規則」）。
 *
 * <p><strong>ADR-008 の概算式を使わない。</strong> 概算は経路候補の並べ替え用であり、
 * 荷主に見せた瞬間に請求額として読まれるため画面にも出していない
 * （{@code ui_design.md}）。<strong>並べ替えの物差しを請求に使ってはならない。</strong>
 *
 * <p><strong>算出した時点で丸める</strong>（段階丸めの 1 段目）。丸めずに次段へ渡すと、
 * 割引と消費税の丸めが二重にずれる。
 */
public final class FreightChargeCalculator {

    private FreightChargeCalculator() {
    }

    /**
     * 基本料金を算出する。
     *
     * @param distanceFactor 距離係数。<strong>0 は「運んでいない」であり請求できない</strong>
     * @param weightKg       重量（kg）。<strong>0 は入力の誤りである</strong>
     * @param cargoType      貨物種別の係数
     */
    public static Money calculate(
            BigDecimal distanceFactor, BigDecimal weightKg, CargoTypeFactor cargoType) {
        requirePositive(distanceFactor, "距離係数");
        requirePositive(weightKg, "重量");
        if (cargoType == null) {
            throw new IllegalArgumentException("貨物種別は必須です");
        }
        return Money.yen(distanceFactor.multiply(weightKg).multiply(cargoType.factor()));
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + "は 0 より大きい値が必須です: " + value);
        }
    }
}
