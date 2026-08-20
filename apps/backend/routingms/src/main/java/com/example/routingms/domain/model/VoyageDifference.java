package com.example.routingms.domain.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 既にある航海と、これから登録しようとしている内容の差分（US25）。
 *
 * <p>何が変わるか分からないまま上書きさせない。経路設計者は差し替えのつもりで同じ番号を
 * 入れることが多く、そのとき知りたいのは「今と何が違うか」である。
 */
public record VoyageDifference(List<Change> changes) {

    /** 1 項目分の変更。項目名は画面にそのまま出す言葉にする。 */
    public record Change(String item, String before, String after) {
    }

    public boolean hasChanges() {
        return !changes.isEmpty();
    }

    /**
     * 既存と新しい内容を比べる。
     *
     * <p>航海番号は比較しない（同じ番号どうしを比べているため）。
     */
    public static VoyageDifference between(Voyage existing, Voyage incoming) {
        List<Change> changes = new ArrayList<>();
        compare(changes, "船名", existing.vesselName(), incoming.vesselName());
        compare(changes, "運送会社", existing.carrierName(), incoming.carrierName());
        compare(changes, "対応できる貨物種別",
                describeCargoTypes(existing.supportedCargoTypes()),
                describeCargoTypes(incoming.supportedCargoTypes()));
        compare(changes, "寄港地",
                describePorts(existing), describePorts(incoming));
        compare(changes, "出発日時",
                describeInstant(existing), describeInstant(incoming));
        return new VoyageDifference(List.copyOf(changes));
    }

    private static void compare(List<Change> changes, String item, String before, String after) {
        if (!before.equals(after)) {
            changes.add(new Change(item, before, after));
        }
    }

    private static String describeCargoTypes(Set<CargoType> cargoTypes) {
        // 並びを固定する。順序が揺れると、内容が同じでも「変わった」と見える
        return Arrays.stream(CargoType.values())
                .filter(cargoTypes::contains)
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }

    private static String describePorts(Voyage voyage) {
        return voyage.schedule().callingPorts().stream()
                .map(com.example.shared.domain.model.Location::unLocode)
                .collect(Collectors.joining(" → "));
    }

    private static String describeInstant(Voyage voyage) {
        return voyage.schedule().carrierMovements().get(0).departureTime().toString();
    }
}
