package com.example.billingms.domain.model.valueobjects;

import java.math.BigDecimal;

/**
 * 貨物種別（[ADR-027] 決定 1）。
 *
 * <p><strong>bookingms の {@code CargoType} とは別の型である。</strong>同じ名前で同じ 3 値を
 * 持つが、こちらが知っているのは「運賃の係数がいくつか」だけで、寸法も危険物申告も
 * 温度条件も持たない。ACL が文字列から変換する——相手の型を持ち込むと、bookingms の
 * 変更がこちらのコンパイルを壊す。
 */
public enum CargoType {

    /** 一般貨物。基準となる係数。 */
    GENERAL(new BigDecimal("1.0"), "一般貨物"),

    /**
     * 危険物。<strong>取り扱いに専用の設備と手順が要る。</strong>
     *
     * <p>係数を 1.0 に戻すと、危険物が一般貨物と同じ運賃になる（`CargoTypeTest` が守る）。
     */
    HAZARDOUS(new BigDecimal("1.8"), "危険物"),

    /** 冷凍・冷蔵。<strong>航海のあいだ電力を使い続ける。</strong> */
    REFRIGERATED(new BigDecimal("1.5"), "冷凍・冷蔵貨物");

    private final BigDecimal factor;

    private final String label;

    CargoType(BigDecimal factor, String label) {
        this.factor = factor;
        this.label = label;
    }

    /** 運賃の係数。 */
    public BigDecimal factor() {
        return factor;
    }

    /**
     * 画面に出す表示名。
     *
     * <p><strong>列挙が自分で持つ。</strong>応答の組み立て側に名簿を置くと、
     * 値を足したときに載せ忘れても何も起きず、その貨物だけ英字が画面に出る。
     */
    public String label() {
        return label;
    }

    /**
     * 文字列から引く（ACL 用）。
     *
     * <p><strong>知らない種別は断る。</strong>既定値（一般貨物）に倒すと、
     * <strong>bookingms が新しい種別を足したときに、その貨物だけ安く請求される</strong>
     * ——名簿方式の検査は「載っていないもの」を通すと、載せ忘れたものほど漏れる。
     */
    public static CargoType of(String name) {
        if (name == null) {
            throw new IllegalArgumentException("貨物種別を指定してください");
        }
        for (CargoType type : values()) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        throw new IllegalArgumentException("扱いを決めていない貨物種別です: " + name);
    }
}
