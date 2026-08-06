package com.example.cargotracker.booking.domain.model;

import com.example.cargotracker.shared.domain.model.Location;
import java.time.LocalDate;

/**
 * ルート仕様。出発地・目的地・到着期限という輸送の要件。
 *
 * <p>ビジネスルール 2（{@code domain-model.md}）: 出発地と目的地は異なる。
 * DB の {@code chk_cargo_origin_destination} と二重に守る。**制約を DB だけに置くと、
 * 違反したときに業務のことばではなく SQL のエラーが返る。**
 *
 * @param origin          出発地
 * @param destination     目的地
 * @param arrivalDeadline 到着期限（日付単位。時刻を持たない）
 */
public record RouteSpecification(Location origin, Location destination, LocalDate arrivalDeadline) {

    public RouteSpecification {
        if (origin == null || destination == null) {
            throw new IllegalArgumentException("出発地と目的地は必須です");
        }
        if (origin.equals(destination)) {
            throw new IllegalArgumentException(
                    "出発地と目的地は異なる地点でなければなりません: " + origin.unlocode());
        }
        if (arrivalDeadline == null) {
            throw new IllegalArgumentException("到着期限は必須です");
        }
    }

    /**
     * 到着期限が過去でないことを検証したうえで生成する。
     *
     * <p>基準日を引数に取るのは、テストが時計に依存しないようにするためである。
     * **`LocalDate.now()` を内部で呼ぶと、日付が変わる瞬間にだけ落ちるテストになる。**
     *
     * <p>比較は日付単位で行う（ビジネスルール 2-1）。**当日は過去ではない。**
     * 当日着の予約は業務上ありふれており、これを弾くと受付ができなくなる。
     *
     * @param today 基準日
     */
    public static RouteSpecification of(
            Location origin, Location destination, LocalDate arrivalDeadline, LocalDate today) {
        if (arrivalDeadline != null && arrivalDeadline.isBefore(today)) {
            throw new IllegalArgumentException("到着期限に過去の日付は指定できません: " + arrivalDeadline);
        }
        return new RouteSpecification(origin, destination, arrivalDeadline);
    }
}
