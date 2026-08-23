package com.example.handlingms.interfaces.rest;

import com.example.shared.domain.model.Location;

/**
 * 作業場所の選択肢（US15-3）。
 *
 * <p>自由入力にしないために返す。綴りの揺れた港が記録に入ると、照合が働かなくなる。
 *
 * @param unLocode UN/LOCODE
 * @param name 地点名
 */
public record LocationResponse(String unLocode, String name) {

    public static LocationResponse from(Location location) {
        return new LocationResponse(location.unLocode(), location.name());
    }
}
