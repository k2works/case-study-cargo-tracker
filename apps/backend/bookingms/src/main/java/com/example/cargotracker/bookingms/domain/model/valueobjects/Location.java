package com.example.cargotracker.bookingms.domain.model.valueobjects;

import java.util.Objects;

/**
 * 港湾（位置）を表す値オブジェクト。
 *
 * <p>shared モジュール（共有カーネル）の候補だが、IT2 時点では bookingms 内に配置する。
 * routingms スケルトン作成後（IT2 タスク 3.x）に shared へ昇格する。</p>
 */
public record Location(UnLocode unLocode, String portName) {

    public Location {
        Objects.requireNonNull(unLocode, "unLocode");
        // portName は任意（マスタ未連携の段階では null/空）
    }

    /** UN/LOCODE のみで生成する（portName は null）。 */
    public static Location of(String unLocode) {
        return new Location(new UnLocode(unLocode), null);
    }
}
