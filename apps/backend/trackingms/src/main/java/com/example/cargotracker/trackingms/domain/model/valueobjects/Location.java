package com.example.cargotracker.trackingms.domain.model.valueobjects;

import java.util.Objects;

/**
 * 港湾（位置）を表す値オブジェクト。
 *
 * <p>shared モジュール昇格候補。bookingms / routingms / handlingms の同名 VO と同一仕様。</p>
 */
public record Location(UnLocode unLocode, String portName) {

    public Location {
        Objects.requireNonNull(unLocode, "unLocode");
        // portName は任意
    }

    /** UN/LOCODE のみで生成する（portName は null）。 */
    public static Location of(String unLocode) {
        return new Location(new UnLocode(unLocode), null);
    }
}
