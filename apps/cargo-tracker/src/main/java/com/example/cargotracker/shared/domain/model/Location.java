package com.example.cargotracker.shared.domain.model;

import java.util.regex.Pattern;

/**
 * 地点。UN/LOCODE で識別する港・内陸地点。<strong>共有カーネル</strong>（ADR-005）。
 *
 * <p>共有カーネルに置いてよいのは本クラスと {@code ShipperId} の 2 要素のみである。
 * UN/LOCODE は国際標準であり、港の識別という意味はどの BC でも同一で解釈が分岐しない。
 *
 * @param unlocode UN/LOCODE（英大文字 5 文字。前 2 文字が国コード）
 */
public record Location(String unlocode) {

    private static final Pattern FORMAT = Pattern.compile("[A-Z]{2}[A-Z0-9]{3}");

    public Location {
        if (unlocode == null || !FORMAT.matcher(unlocode).matches()) {
            throw new IllegalArgumentException(
                    "地点は UN/LOCODE（英大文字 5 文字）で指定します: " + unlocode);
        }
    }

    public static Location of(String unlocode) {
        return new Location(unlocode);
    }
}
