package com.example.billingms.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.List;

/**
 * 基本料金と、その根拠（[ADR-027] 決定 1）。
 *
 * <pre>
 * 基本料金 = 基準運賃 × 区間係数 × 重量係数 × 貨物種別係数
 * </pre>
 *
 * <p><strong>距離は使わない。</strong>港のマスタに緯度経度が無く、航海も距離を持たない。
 * 区間で代替する——旅程は経路設計者が確定し、荷役の実績と突き合わせ済みで、
 * 後から変わらない。
 *
 * <p><strong>区間は数えるだけでは足りない</strong>（IT12 の改訂）。区間数だけで測ると
 * 東京 → 横浜と東京 → ロサンゼルスが同額になる。<strong>区間ごとに地域区分の係数を
 * 足し合わせる</strong>——国内だけの旅程では改訂前と同じ金額になる。
 *
 * <p><strong>根拠を持ったまま渡す。</strong>金額だけを返すと、画面が「なぜその金額か」を
 * 出せない——経理担当者は請求の根拠を荷主に説明する。
 *
 * @param legCount 旅程の区間数
 * @param legFactor 区間係数（区間ごとの地域係数の合計。<strong>距離の代わり</strong>）
 * @param region 旅程で最も重い地域区分。<strong>根拠として画面に出す</strong>。
 *        運んでいない貨物では {@code null}
 * @param weightKg 重量（kg）
 * @param cargoType 貨物種別
 */
public record TransportCharge(int legCount, BigDecimal legFactor, PortRegion region,
        BigDecimal weightKg, CargoType cargoType) {

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
        if (legCount < 0) {
            throw new IllegalArgumentException("区間数が負です: " + legCount);
        }
        if (legFactor == null || legFactor.signum() < 0) {
            throw new IllegalArgumentException("区間係数が負です: " + legFactor);
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
        // 区間係数も同じ理由で桁数を揃える。NUMERIC(8,2) から戻すと 2.00 になり、
        // 掛け算の結果（2.0）と equals が食い違う
        legFactor = legFactor.stripTrailingZeros();
    }

    /**
     * 旅程から作る。
     *
     * <p>区間係数は<strong>区間ごとの地域係数の合計</strong>である
     * （[ADR-027] 決定 1 の改訂）。
     */
    public static TransportCharge of(List<ChargeableLeg> legs, BigDecimal weightKg,
            CargoType cargoType) {
        if (legs == null || legs.isEmpty()) {
            // **運んだ貨物には旅程がある。** 0 を運賃の計算に通すと 0 円になり、
            // 運んだのに請求しないことになる。運んでいない貨物（経路が決まる前の
            // キャンセル）は {@link #notTransported} で作る——**同じ 0 区間でも、
            // 「運んでいない」と「データが壊れている」は別である**
            throw new IllegalArgumentException("区間が 1 本も無い旅程では料金を算定できません");
        }
        BigDecimal factor = legs.stream().map(ChargeableLeg::factor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        PortRegion heaviest = legs.stream().map(ChargeableLeg::region)
                .reduce((left, right) ->
                        left.factor().compareTo(right.factor()) >= 0 ? left : right)
                .orElseThrow();
        return new TransportCharge(legs.size(), factor, heaviest, weightKg, cargoType);
    }

    /**
     * 保存した値から戻す（[ADR-027] 決定 4）。
     *
     * <p><strong>再計算しない。</strong>発行した精算書の金額は動かないため、
     * 発行時に確定した区間係数をそのまま戻す——区間の並びは保存していない。
     * <strong>復元では検査しない</strong>（列が無かったころの行が読めなくなる）。
     */
    public static TransportCharge restored(int legCount, BigDecimal legFactor, PortRegion region,
            BigDecimal weightKg, CargoType cargoType) {
        return new TransportCharge(legCount, legFactor, region, weightKg, cargoType);
    }

    /**
     * 運んでいない貨物（IT11 レビュー 高 1）。
     *
     * <p>経路が決まる前にキャンセルされた予約は旅程を持たない。{@code CancelledAtStatus} が
     * {@code PRELIMINARY} / {@code ROUTE_PROPOSED} に料率 0% を定義しており、
     * <strong>業務として想定されている</strong>——それでも精算の一覧には並ぶ
     * （キャンセル料 0 円として締める）。
     *
     * <p><strong>運んだ貨物には使わない。</strong>引取済なのに旅程が無いのはデータが
     * 壊れており、0 円で通すと運んだのに請求しないことになる。
     */
    public static TransportCharge notTransported(BigDecimal weightKg, CargoType cargoType) {
        return new TransportCharge(0, BigDecimal.ZERO, null, weightKg, cargoType);
    }

    /** 運んでいない貨物か（区間が 1 本も無い）。 */
    public boolean notTransported() {
        return legCount == 0;
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
        // **運んでいなければ 0 円。** 区間係数を掛けても同じ値になるが、
        // 「運んでいないから 0」と「掛けたら 0 になった」は別の意味である
        return notTransported()
                ? Money.zero()
                : BASE_FARE.multiply(legFactor.multiply(weightFactor())
                        .multiply(cargoTypeFactor()));
    }
}
