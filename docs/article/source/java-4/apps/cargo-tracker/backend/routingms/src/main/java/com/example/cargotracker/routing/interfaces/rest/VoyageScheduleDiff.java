package com.example.cargotracker.routing.interfaces.rest;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 更新前後の差分（US25 §受入基準 2）。
 *
 * <p><b>サーバが出す。</b> 画面で 2 つの値を並べて {@code if} を積み上げると、航海に属性が
 * 増えるたびに比べ忘れが生まれる（IT3 の投影で実際に起きた形）。</p>
 *
 * <p><b>丸ごと比べる。</b> 比べる対象は {@link VoyageSnapshot} のレコード要素そのもので、
 * 項目を列挙した名簿は持たない。名簿を手で書くと、要素を足したときに書き忘れたものが
 * 黙って差分から消える。<b>ラベルの無い要素は例外にする</b>（載っていないものを通す
 * 名簿は、載せ忘れたものほど漏れる）。</p>
 */
public final class VoyageScheduleDiff {

    /** 画面に出す名前。要素を足したらここにも足す。足さなければ実行時に落ちる。 */
    private static final Map<String, String> LABELS = Map.of(
            "carrierCode", "運送会社コード",
            "carrierName", "運送会社",
            "vesselName", "船名",
            "acceptedCargoTypes", "対応貨物種別",
            "movements", "寄港地");

    private VoyageScheduleDiff() {
    }

    /** 更新で置き換わる内容。<b>ここに無い値は差分に出ない。</b> */
    public record VoyageSnapshot(
            String carrierCode,
            String carrierName,
            String vesselName,
            List<String> acceptedCargoTypes,
            List<Movement> movements) {

        /** 寄港地。並び順そのものが業務の意味を持つ。 */
        public record Movement(
                String departureUnLocode,
                String arrivalUnLocode,
                Instant departureAt,
                Instant arrivalAt) {

            @Override
            public String toString() {
                return departureUnLocode + " → " + arrivalUnLocode
                        + "（" + departureAt + " 〜 " + arrivalAt + "）";
            }
        }
    }

    /** 変わった項目 1 つ。 */
    public record FieldChange(String label, String before, String after) {
    }

    public static List<FieldChange> between(VoyageSnapshot before, VoyageSnapshot after) {
        List<FieldChange> changes = new ArrayList<>();
        for (RecordComponent component : VoyageSnapshot.class.getRecordComponents()) {
            Object left = valueOf(before, component);
            Object right = valueOf(after, component);
            if (!java.util.Objects.equals(left, right)) {
                changes.add(new FieldChange(labelOf(component.getName()),
                        display(left), display(right)));
            }
        }
        return List.copyOf(changes);
    }

    /** 比べている項目の名前。名簿と実際の比較がずれていないことを検査から見る。 */
    public static List<String> comparedFields() {
        return List.of(VoyageSnapshot.class.getRecordComponents()).stream()
                .map(RecordComponent::getName)
                .toList();
    }

    static String labelOf(String field) {
        String label = LABELS.get(field);
        if (label == null) {
            throw new IllegalStateException(
                    "差分に出す名前が決まっていない項目です: " + field);
        }
        return label;
    }

    private static Object valueOf(VoyageSnapshot snapshot, RecordComponent component) {
        try {
            return component.getAccessor().invoke(snapshot);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("差分を読めません: " + component.getName(), e);
        }
    }

    private static String display(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).reduce((a, b) -> a + " / " + b).orElse("");
        }
        return String.valueOf(value);
    }
}
