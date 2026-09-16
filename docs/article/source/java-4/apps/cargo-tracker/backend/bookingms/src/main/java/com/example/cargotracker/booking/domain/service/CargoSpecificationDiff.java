package com.example.cargotracker.booking.domain.service;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 修正前後の差分（US32 §受入基準 4「何を変えたか」）。
 *
 * <p>IT4 では修正した事実（{@code updated_at} / {@code updated_by}）だけを投影に置き、
 * 変更内容は Event Store が持つとした。<b>その結果、何を変えたかは誰にも読めなかった</b>
 * （IT4 引き継ぎ 2）。記録だけして読み口を出さないと、記録は無いのと同じである。
 * 判断の経緯は ADR-0008。</p>
 *
 * <p><b>丸ごと比べる。</b> 比べる対象は {@link CargoSnapshot} のレコード要素そのもので、
 * 項目を列挙した名簿は持たない。名簿を手で書くと、要素を足したときに書き忘れたものが
 * 黙って差分から消える。<b>ラベルの無い要素は例外にする</b>（載っていないものを通す
 * 名簿は、載せ忘れたものほど漏れる）。</p>
 */
public final class CargoSpecificationDiff {

    /** 画面に出す名前。要素を足したらここにも足す。足さなければ実行時に落ちる。 */
    private static final Map<String, String> LABELS = labels();

    private static Map<String, String> labels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("originUnLocode", "出発地");
        labels.put("destinationUnLocode", "目的地");
        labels.put("arrivalDeadline", "希望着日");
        labels.put("cargoType", "貨物種別");
        labels.put("weightKg", "重量(kg)");
        labels.put("lengthCm", "長さ(cm)");
        labels.put("widthCm", "幅(cm)");
        labels.put("heightCm", "高さ(cm)");
        labels.put("quantity", "個数");
        labels.put("productName", "品名");
        labels.put("hazardImoClass", "IMO クラス");
        labels.put("hazardUnNumber", "国連番号");
        labels.put("temperatureMinC", "温度下限(℃)");
        labels.put("temperatureMaxC", "温度上限(℃)");
        return Map.copyOf(labels);
    }

    private CargoSpecificationDiff() {
    }

    /** 修正で置き換わる内容。<b>ここに無い値は差分に出ない。</b> */
    public record CargoSnapshot(
            String originUnLocode,
            String destinationUnLocode,
            LocalDate arrivalDeadline,
            String cargoType,
            BigDecimal weightKg,
            BigDecimal lengthCm,
            BigDecimal widthCm,
            BigDecimal heightCm,
            int quantity,
            String productName,
            String hazardImoClass,
            String hazardUnNumber,
            BigDecimal temperatureMinC,
            BigDecimal temperatureMaxC) {
    }

    /** 変わった項目 1 つ。 */
    public record FieldChange(String label, String before, String after) {
    }

    public static List<FieldChange> between(CargoSnapshot before, CargoSnapshot after) {
        List<FieldChange> changes = new ArrayList<>();
        for (RecordComponent component : CargoSnapshot.class.getRecordComponents()) {
            Object left = valueOf(before, component);
            Object right = valueOf(after, component);
            if (!sameValue(left, right)) {
                changes.add(new FieldChange(labelOf(component.getName()),
                        display(left), display(right)));
            }
        }
        return List.copyOf(changes);
    }

    /**
     * 同じ値か。
     *
     * <p><b>{@code BigDecimal} は {@code equals} で比べない。</b> {@code 1200} と
     * {@code 1200.00} は等しくないと判定され、桁が揃っていないだけで
     * 「重量を変えた」が出る（投影は {@code NUMERIC} の位取りで返す）。</p>
     */
    private static boolean sameValue(Object left, Object right) {
        if (left instanceof BigDecimal a && right instanceof BigDecimal b) {
            return a.compareTo(b) == 0;
        }
        return Objects.equals(left, right);
    }

    /** 比べている項目の名前。名簿と実際の比較がずれていないことを検査から見る。 */
    public static List<String> comparedFields() {
        return List.of(CargoSnapshot.class.getRecordComponents()).stream()
                .map(RecordComponent::getName)
                .toList();
    }

    static String labelOf(String field) {
        String label = LABELS.get(field);
        if (label == null) {
            throw new IllegalStateException("差分に出す名前が決まっていない項目です: " + field);
        }
        return label;
    }

    private static Object valueOf(CargoSnapshot snapshot, RecordComponent component) {
        try {
            return component.getAccessor().invoke(snapshot);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("差分を読めません: " + component.getName(), e);
        }
    }

    /** 未入力は空欄と読めるようにする。{@code null} と出すと画面に生の値が並ぶ。 */
    private static String display(Object value) {
        return value == null ? "（未入力）" : String.valueOf(value);
    }
}
