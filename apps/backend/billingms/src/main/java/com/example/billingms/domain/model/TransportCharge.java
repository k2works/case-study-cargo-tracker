package com.example.billingms.domain.model;

import java.math.BigDecimal;

/**
 * 基本料金と、その根拠（[ADR-027] 決定 1）。
 *
 * <pre>
 * 基本料金 = 基準運賃 × 区間係数 × 重量係数 × 貨物種別係数
 * </pre>
 *
 * <p><strong>距離は使わない。</strong>港のマスタに緯度経度が無く、航海も距離を持たない。
 * 区間数で代替する——区間数が「どれだけ運んだか」に比例する唯一の実測値である。
 * 旅程は経路設計者が確定し、荷役の実績と突き合わせ済みで、後から変わらない。
 *
 * <p><strong>根拠を持ったまま渡す。</strong>金額だけを返すと、画面が「なぜその金額か」を
 * 出せない——経理担当者は請求の根拠を荷主に説明する。
 *
 * @param legCount 旅程の区間数（<strong>距離の代わり</strong>）
 * @param weightKg 重量（kg）
 * @param cargoType 貨物種別
 */
public record TransportCharge(int legCount, BigDecimal weightKg, CargoType cargoType) {

    /** 1 区間・1,000kg・一般貨物のときの運賃。 */
    public static final Money BASE_FARE = Money.yen(new BigDecimal("50000"));

    /** 重量係数の基準（kg）。 */
    private static final BigDecimal WEIGHT_UNIT = new BigDecimal("1000");

    /**
     * 重量係数の下限。
     *
     * <p><strong>運ぶ手間は重量に比例しない。</strong>置かないと、軽量の貨物が
     * 0 円に近づく。
     */
    private static final BigDecimal MIN_WEIGHT_FACTOR = new BigDecimal("0.1");

    public TransportCharge {
        if (legCount <= 0) {
            // **0 を通すと料金が 0 円になる。** 運んだのに請求しないことになる
            throw new IllegalArgumentException(
                    "区間が 1 本も無い旅程では料金を算定できません: " + legCount);
        }
        if (weightKg == null || weightKg.signum() <= 0) {
            throw new IllegalArgumentException("重量は 0 より大きい値で指定してください: " + weightKg);
        }
        if (cargoType == null) {
            throw new IllegalArgumentException("貨物種別を指定してください");
        }
        // **桁数を揃えて持つ**（{@link Money} と同じ扱い）。DB から読み戻した重量は列の
        // 桁数どおりの端数を持つ（NUMERIC(10,3) なら 4200.000）。BigDecimal の equals は
        // 桁数まで見るため、揃えないと「書いたとおりに戻ったか」を確かめる検査が、
        // 書いたとおりに戻っているのに落ちる
        weightKg = weightKg.stripTrailingZeros();
    }

    public static TransportCharge of(int legCount, BigDecimal weightKg, CargoType cargoType) {
        return new TransportCharge(legCount, weightKg, cargoType);
    }

    /** 区間係数。**距離の代わり**（決定 1）。 */
    public BigDecimal legFactor() {
        return BigDecimal.valueOf(legCount);
    }

    /** 重量係数。下限を下回らない。 */
    public BigDecimal weightFactor() {
        BigDecimal factor = weightKg.divide(WEIGHT_UNIT, 4, java.math.RoundingMode.HALF_UP);
        return factor.compareTo(MIN_WEIGHT_FACTOR) < 0 ? MIN_WEIGHT_FACTOR : factor;
    }

    /** 貨物種別係数。 */
    public BigDecimal cargoTypeFactor() {
        return cargoType.factor();
    }

    /**
     * 基本料金。
     *
     * <p><strong>掛けてから丸める</strong>（決定 2）。係数ごとに丸めると、
     * 積み重なるほど誤差が開く。
     */
    public Money baseAmount() {
        return BASE_FARE.multiply(legFactor().multiply(weightFactor()).multiply(cargoTypeFactor()));
    }
}
