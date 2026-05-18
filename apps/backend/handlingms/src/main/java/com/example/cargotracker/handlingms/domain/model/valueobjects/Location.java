package com.example.cargotracker.handlingms.domain.model.valueobjects;

import java.util.Objects;

/**
 * 港湾（位置）を表す値オブジェクト。
 *
 * <p>bookingms と同設計（将来 shared モジュールへ昇格予定）。</p>
 */
public record Location(UnLocode unLocode, String portName) {

    public Location {
        Objects.requireNonNull(unLocode, "unLocode");
    }

    /** UN/LOCODE のみで生成する（portName は null）。 */
    public static Location of(String unLocode) {
        return new Location(new UnLocode(unLocode), null);
    }
}
