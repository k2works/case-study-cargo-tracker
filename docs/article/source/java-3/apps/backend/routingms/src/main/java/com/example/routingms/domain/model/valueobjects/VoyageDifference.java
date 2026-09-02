package com.example.routingms.domain.model.valueobjects;

import com.example.routingms.domain.model.aggregates.Voyage;
import com.example.shared.domain.model.Location;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
 *
 * <p>差分は<strong>運航管理者が判断できる形</strong>で出す。列挙名や UTC の機械表記で出すと、
 * 09:00 と入力した相手に 00:00Z を見せて「上書きしますか」と聞くことになり、
 * 判断させているように見えて判断できない。
 */
public record VoyageDifference(List<Change> changes) {

        public VoyageDifference {
        // 受け取った一覧を写して持つ。呼び出し元が渡したものをそのまま抱えると、
        // 渡したあとの書き換えがこちらの中身を変える。null は許す——項目が無いことと
        // 空であることは違う
        changes = changes == null ? null : List.copyOf(changes);
        }


    /** 1 項目分の変更。項目名は画面にそのまま出す言葉にする。 */
    public record Change(String item, String before, String after) {
    }

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public boolean hasChanges() {
        return !changes.isEmpty();
    }

    /**
     * 既存と新しい内容を比べる。
     *
     * <p>航海番号は比較しない（同じ番号どうしを比べているため）。
     *
     * @param businessZone 日時を見せるタイムゾーン。業務の時刻で判断させる
     */
    public static VoyageDifference between(Voyage existing, Voyage incoming, ZoneId businessZone) {
        List<Change> changes = new ArrayList<>();
        compare(changes, "船名", existing.vesselName(), incoming.vesselName());
        compare(changes, "運送会社", existing.carrierName(), incoming.carrierName());
        compare(changes, "対応できる貨物種別",
                describeCargoTypes(existing.supportedCargoTypes()),
                describeCargoTypes(incoming.supportedCargoTypes()));
        compare(changes, "寄港地",
                describePorts(existing), describePorts(incoming));
        compare(changes, "日程",
                describeSchedule(existing, businessZone),
                describeSchedule(incoming, businessZone));
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
                .map(CargoType::label)
                .collect(Collectors.joining("、"));
    }

    private static String describePorts(Voyage voyage) {
        return voyage.schedule().callingPorts().stream()
                .map(VoyageDifference::describePort)
                .collect(Collectors.joining(" → "));
    }

    /**
     * 全区間の出発・到着を 1 つの文字列にする。
     *
     * <p>先頭区間の出発だけを比べていた頃は、<strong>遅延による時刻の付け替えが差分に
     * 現れず、画面から更新できなかった</strong>。項目ごとに比較を足していくと、航海に属性が
     * 増えるたび同じ穴が空くため、日程は 1 つの表現にまとめて丸ごと比べる。
     */
    private static String describeSchedule(Voyage voyage, ZoneId businessZone) {
        return voyage.schedule().carrierMovements().stream()
                .map(movement -> "%s %s 発 → %s %s 着".formatted(
                        describePort(movement.departureLocation()),
                        format(movement.departureTime(), businessZone),
                        describePort(movement.arrivalLocation()),
                        format(movement.arrivalTime(), businessZone)))
                .collect(Collectors.joining(" ／ "));
    }

    private static String describePort(Location location) {
        return "%s (%s)".formatted(location.name(), location.unLocode());
    }

    private static String format(Instant instant, ZoneId businessZone) {
        return TIME_FORMAT.format(instant.atZone(businessZone));
    }
}
