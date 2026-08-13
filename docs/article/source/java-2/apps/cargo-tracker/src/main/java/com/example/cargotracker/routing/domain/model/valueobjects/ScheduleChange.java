package com.example.cargotracker.routing.domain.model.valueobjects;
import com.example.cargotracker.routing.domain.model.aggregates.Voyage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 航海の変更内容（US25）。**変更前と変更後の差分そのものである。**
 *
 * <p>運航変更の確認画面は「何がどう変わるのか」を示すためにある。
 * <strong>変わらない項目まで並べると、変わったものが埋もれる。</strong>
 * そのため差分は「変わった項目だけ」を持つ。
 *
 * <p>差分の作成は Routing Context のドメインに置く。画面で 2 つの航海を
 * 突き合わせて表示すると、**同じ比較が編集画面・確認画面・監査ログに散る**。
 *
 * @param items 変わった項目
 */
public record ScheduleChange(List<Item> items) {

    public ScheduleChange {
        items = List.copyOf(items);
    }

    /**
     * 変更前後を比較して差分を作る。
     *
     * @param before 変更前の航海
     * @param after  変更後の内容（未保存でよい）
     */
    public static ScheduleChange between(Voyage before, Voyage after) {
        List<Item> items = new ArrayList<>();
        compare(items, "船名", before.vesselName().value(), after.vesselName().value());
        compare(items, "運送会社", before.carrierName().value(), after.carrierName().value());
        compare(items, "積載可能重量",
                before.capacityWeight().kilograms().stripTrailingZeros().toPlainString(),
                after.capacityWeight().kilograms().stripTrailingZeros().toPlainString());
        compare(items, "取扱貨物種別",
                displayCargoTypes(before), displayCargoTypes(after));
        compareMovements(items, before.schedule(), after.schedule());
        return new ScheduleChange(items);
    }

    private static void compareMovements(List<Item> items, Schedule before, Schedule after) {
        List<CarrierMovement> olds = before.carrierMovements();
        List<CarrierMovement> news = after.carrierMovements();
        int max = Math.max(olds.size(), news.size());
        for (int i = 0; i < max; i++) {
            String label = "区間 %d".formatted(i + 1);
            // **区間の増減も差分である。** 寄港地が 1 つ消える運航変更は珍しくない
            compare(items, label,
                    i < olds.size() ? display(olds.get(i)) : "（無し）",
                    i < news.size() ? display(news.get(i)) : "（無し）");
        }
    }

    private static String display(CarrierMovement movement) {
        return "%s %s → %s %s".formatted(
                movement.departureLocation().unlocode(), movement.departureTime(),
                movement.arrivalLocation().unlocode(), movement.arrivalTime());
    }

    private static String displayCargoTypes(Voyage voyage) {
        return voyage.acceptableCargoTypes().stream()
                .map(RoutingCargoType::name)
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    private static void compare(List<Item> items, String label, String before, String after) {
        if (!Objects.equals(before, after)) {
            items.add(new Item(label, before, after));
        }
    }

    /** 変更が 1 件も無いか。**同じ内容での上書きは業務上意味がない。** */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * 変わった項目 1 件。
     *
     * @param label  項目名
     * @param before 変更前の表示
     * @param after  変更後の表示
     */
    public record Item(String label, String before, String after) {
    }
}
