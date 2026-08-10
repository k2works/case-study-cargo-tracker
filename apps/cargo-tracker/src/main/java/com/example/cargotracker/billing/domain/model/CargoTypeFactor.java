package com.example.cargotracker.billing.domain.model;

import java.math.BigDecimal;

/**
 * 貨物種別ごとの料金係数（US21。{@code domain-model.md} の計算式）。
 *
 * <p><strong>Booking の {@code CargoType} を共有しない</strong>（ADR-005）。
 * BC をまたいで運べるのは素の値だけである。ここが持つのは
 * <strong>請求に効く係数</strong>であり、予約の分類そのものではない。
 *
 * <p><strong>係数を画面や計算側に書き写さない。</strong> 2 か所にあると、
 * 料率を改定したときに片方だけが古くなる。
 */
public enum CargoTypeFactor {

    /** 一般貨物。 */
    GENERAL("一般貨物", new BigDecimal("1.0")),

    /** 危険物。**取り扱いの手間とリスクが違う。** */
    HAZARDOUS("危険物", new BigDecimal("1.8")),

    /** 冷凍・冷蔵。**温度を保つ設備が要る。** */
    REFRIGERATED("冷凍・冷蔵", new BigDecimal("1.5"));

    private final String displayName;
    private final BigDecimal factor;

    CargoTypeFactor(String displayName, BigDecimal factor) {
        this.displayName = displayName;
        this.factor = factor;
    }

    /** 画面に出す日本語名。**列挙子名を利用者に見せない。** */
    public String displayName() {
        return displayName;
    }

    /** 料金係数。 */
    public BigDecimal factor() {
        return factor;
    }

    /**
     * 予約から届いた種別名で引く。
     *
     * <p><strong>知らない種別は一般貨物として扱わない。</strong> 黙って 1.0 を当てると、
     * 危険物を一般料金で請求することになる。
     */
    public static CargoTypeFactor of(String cargoType) {
        if (cargoType == null || cargoType.isBlank()) {
            throw new IllegalArgumentException("貨物種別は必須です");
        }
        for (CargoTypeFactor value : values()) {
            if (value.name().equalsIgnoreCase(cargoType.strip())) {
                return value;
            }
        }
        throw new IllegalArgumentException("料金係数が定義されていない貨物種別です: " + cargoType);
    }
}
