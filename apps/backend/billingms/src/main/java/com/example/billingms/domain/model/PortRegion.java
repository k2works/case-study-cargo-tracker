package com.example.billingms.domain.model;

import java.math.BigDecimal;

/**
 * 地点の地域区分（[ADR-027] 決定 1 の改訂）。
 *
 * <p><strong>距離の代わりである。</strong>港のマスタに緯度経度が無く、全港の座標整備は
 * IT12 の範囲を超える。区分なら 1 列で足りる。
 *
 * <p>区間数だけで測っていた IT11 では、<strong>東京 → 横浜と東京 → ロサンゼルスが
 * 同額</strong>になっていた。経理担当者は「これでは荷主が納得しない」と述べている。
 */
public enum PortRegion {

    /** 国内。基準となる係数。 */
    DOMESTIC(new BigDecimal("1.0"), "国内"),

    /** 近海（アジア域内）。 */
    NEAR_SEA(new BigDecimal("2.5"), "近海"),

    /** 遠洋（大洋を渡る）。 */
    OCEAN(new BigDecimal("6.0"), "遠洋");

    private final BigDecimal factor;

    private final String label;

    PortRegion(BigDecimal factor, String label) {
        this.factor = factor;
        this.label = label;
    }

    /** 区間係数。 */
    public BigDecimal factor() {
        return factor;
    }

    /** 画面に出す表示名。**なぜその金額かを読めるようにする**（決定 1）。 */
    public String label() {
        return label;
    }

    /**
     * 文字列から引く（ACL 用）。
     *
     * <p><strong>知らない区分は断る。</strong>既定値（国内）に倒すと、地点マスタに
     * 新しい区分を足したときに、その港を通る貨物だけ安く請求される
     * ——名簿方式は載っていないものを通すため、載せ忘れたものほど漏れる。
     */
    public static PortRegion of(String name) {
        if (name == null) {
            throw new IllegalArgumentException("地域区分を指定してください");
        }
        for (PortRegion region : values()) {
            if (region.name().equals(name)) {
                return region;
            }
        }
        throw new IllegalArgumentException("扱いを決めていない地域区分です: " + name);
    }
}
