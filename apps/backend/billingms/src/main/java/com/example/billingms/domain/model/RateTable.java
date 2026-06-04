package com.example.billingms.domain.model;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 料金単価表（domain-model.md L941-945）。
 *
 * <p>貨物種別ごとの単価係数（円 / kg / km）と取扱費（円 / 回）を保持する値オブジェクト。
 * {@link FareCalculator} が基本料金計算で参照する。</p>
 *
 * <p>default テーブルは S20 UI のサンプル値（5300km × 1200kg × 0.05 = 318,000 円、
 * 8 回 × 1500 円 = 12,000 円）と整合する係数を採用する。本テーブルは IT7 ではコード内
 * 定数だが、IT8 以降で運用設定（DB）への移行を検討する（経理担当者が料金改定可能に）。</p>
 *
 * <p>不変性: コンストラクタで rates Map を copy-on-construct（防御的コピー）し、
 * 外部からの変更を拒否する。</p>
 *
 * @param rates           貨物種別（GENERAL / HAZARDOUS / REFRIGERATED 等）→ 単価係数（円/kg/km）
 * @param handlingUnitFee 1 回あたり取扱費（円、0 以上）
 */
public record RateTable(
        Map<String, BigDecimal> rates,
        BigDecimal handlingUnitFee
) {

    public RateTable {
        if (rates == null || rates.isEmpty()) {
            throw new IllegalArgumentException("rates は 1 件以上の単価エントリが必須です");
        }
        if (handlingUnitFee == null || handlingUnitFee.signum() < 0) {
            throw new IllegalArgumentException(
                    "handlingUnitFee は 0 以上の値で必須です: " + handlingUnitFee);
        }
        rates = Map.copyOf(rates); // 防御的コピー
    }

    /**
     * default テーブル（S20 UI サンプル値と整合）。GENERAL=0.05, HAZARDOUS=0.08,
     * REFRIGERATED=0.10、取扱費 1500 円/回。
     */
    public static RateTable defaultTable() {
        return new RateTable(
                Map.of(
                        "GENERAL",      new BigDecimal("0.05"),
                        "HAZARDOUS",    new BigDecimal("0.08"),
                        "REFRIGERATED", new BigDecimal("0.10")
                ),
                new BigDecimal("1500")
        );
    }

    /**
     * 指定された貨物種別の単価係数を返す。
     *
     * @param cargoType 貨物種別
     * @return 単価係数
     * @throws IllegalArgumentException 未知の貨物種別
     */
    public BigDecimal cargoTypeFactor(String cargoType) {
        BigDecimal factor = rates.get(cargoType);
        if (factor == null) {
            throw new IllegalArgumentException(
                    "未知の貨物種別です: " + cargoType + ", 既知=" + rates.keySet());
        }
        return factor;
    }
}
