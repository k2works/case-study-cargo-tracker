package com.example.cargotracker.booking.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 温度管理条件（US05）。
 *
 * <p><strong>上下が同じ指定は通す。</strong> 定温輸送（医薬品・精密機器）は
 * 実務にある。「範囲」という語に引きずって上下を必ず離すと、
 * 実在する輸送が登録できなくなる。
 *
 * <p><strong>範囲と単位はひと組である。</strong> 単位を持たない温度は指示にならず、
 * 上下の片方だけでは「守れているか」を判断できない。
 *
 * @param minTemperature 最低温度
 * @param maxTemperature 最高温度
 * @param unit           単位
 */
public record TemperatureRequirement(
        BigDecimal minTemperature, BigDecimal maxTemperature, TemperatureUnit unit) {

    public TemperatureRequirement {
        if (minTemperature == null || maxTemperature == null) {
            throw new IllegalArgumentException("最低温度と最高温度は必須です");
        }
        if (unit == null) {
            throw new IllegalArgumentException("温度の単位は必須です");
        }
        // **絶対零度より低い温度は物理的に存在しない。** -999 は桁の打ち間違いとして
        // 起きる。上下の大小だけを見る検査はこれを通し、**どの設備でも守れない条件の
        // 貨物を預かる**ことになる。下限は単位ごとに違う（摂氏の -273.15 は
        // 華氏では有効な温度である）
        if (unit.isBelowAbsoluteZero(minTemperature) || unit.isBelowAbsoluteZero(maxTemperature)) {
            throw new IllegalArgumentException(
                    "絶対零度（%s %s）を下回る温度は指定できません"
                            .formatted(unit.absoluteZero().toPlainString(), unit.symbol()));
        }
        // **入れ違いは打ち間違いとして日常的に起きる。** 通すと、どの温度帯でも
        // 条件を満たさない貨物を預かることになる
        if (minTemperature.compareTo(maxTemperature) > 0) {
            throw new IllegalArgumentException(
                    "最低温度が最高温度を上回っています: %s > %s"
                            .formatted(minTemperature, maxTemperature));
        }
    }

    /** 3 つがそろっていれば条件を作る。1 つでも欠けていれば空を返す。 */
    public static Optional<TemperatureRequirement> ofNullable(
            BigDecimal minTemperature, BigDecimal maxTemperature, String unit) {
        if (minTemperature == null || maxTemperature == null || unit == null || unit.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new TemperatureRequirement(
                minTemperature, maxTemperature, TemperatureUnit.valueOf(unit)));
    }

    /** 画面に出す表記（{@code -25.0 ℃ 〜 -18.0 ℃}）。 */
    public String display() {
        return "%s %s 〜 %s %s".formatted(
                minTemperature.toPlainString(), unit.symbol(),
                maxTemperature.toPlainString(), unit.symbol());
    }
}
