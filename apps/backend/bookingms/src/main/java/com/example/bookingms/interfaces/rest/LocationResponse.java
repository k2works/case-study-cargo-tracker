package com.example.bookingms.interfaces.rest;

import com.example.shared.domain.model.Location;

/** 地点。画面が選択肢を出すために使う（コードの直接入力はさせない）。 */
public record LocationResponse(String unLocode, String name) {

    public static LocationResponse from(Location location) {
        return new LocationResponse(location.unLocode(), location.name());
    }
}
