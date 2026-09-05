package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.UnLocode;
import java.time.Instant;

/**
 * 航海スケジュールの検索条件（US07。domain-model.md「Routing BC」）。
 *
 * <p><b>「空文字は指定なし」をここ 1 か所で決める。</b> 画面とクエリハンドラの両方で
 * 判断すると、片方だけが空文字をそのまま渡し、どの航海にも一致せず一覧が黙って空になる
 * （IT3 で貨物種別の絞り込みに実際に起きた形）。</p>
 *
 * <p><b>港湾制約と経路探索はここに入れない。</b> US07 で扱うのは航海スケジュール自身の
 * 条件（出発地・目的地・出発期間・貨物種別）だけで、経路の探索は US08（IT5）。</p>
 *
 * <p>値は素の文字列で持つ。投影への問い合わせに渡すので、値オブジェクトのまま運ぶと
 * Mapper が中身を取り出す形になり、検査の位置が分かりにくくなる。<b>検査はここで通す。</b></p>
 */
public record VoyageSearchCriteria(
        String departure,
        String arrival,
        Instant departFrom,
        Instant departTo,
        String cargoType) {

    public static VoyageSearchCriteria of(String departure, String arrival,
            Instant departFrom, Instant departTo, String cargoType) {
        if (departFrom != null && departTo != null && departTo.isBefore(departFrom)) {
            throw new BusinessRuleViolation("出発期間の開始は終了より後にできません");
        }
        return new VoyageSearchCriteria(
                port(departure), port(arrival),
                departFrom, departTo, cargoTypeName(cargoType));
    }

    /** 条件が 1 つも無い（既定の一覧と同じ）。0 件の案内を出し分けるのに使う。 */
    public boolean isEmpty() {
        return departure == null && arrival == null
                && departFrom == null && departTo == null && cargoType == null;
    }

    private static String port(String value) {
        if (isBlank(value)) {
            return null;
        }
        // 値オブジェクトを通す。ここで素通りさせると、小文字の港が条件に入り、
        // どの航海にも一致しないまま 0 件になる（誤りだと気づけない）。
        return new UnLocode(value.trim()).value();
    }

    private static String cargoTypeName(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return CargoType.valueOf(value.trim()).name();
        } catch (IllegalArgumentException e) {
            // 知らない種別で絞ると 0 件になる。0 件は「無い」と読めるので断る。
            throw new BusinessRuleViolation("知らない貨物種別です: " + value);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
