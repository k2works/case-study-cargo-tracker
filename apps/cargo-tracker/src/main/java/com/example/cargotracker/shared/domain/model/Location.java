package com.example.cargotracker.shared.domain.model;

import java.util.Map;
import java.util.regex.Pattern;

public record Location(String unlocode) {

    private static final Pattern UNLOCODE_PATTERN = Pattern.compile("^[A-Z0-9]{5}$");

    private static final Map<String, String> CITY_NAMES = Map.ofEntries(
            Map.entry("JPTYO", "東京"),
            Map.entry("JPOSK", "大阪"),
            Map.entry("JPOSA", "大阪"),
            Map.entry("JPNGO", "名古屋"),
            Map.entry("JPYOK", "横浜"),
            Map.entry("USNYC", "ニューヨーク"),
            Map.entry("USLAX", "ロサンゼルス"),
            Map.entry("USCHI", "シカゴ"),
            Map.entry("SGSIN", "シンガポール"),
            Map.entry("HKHKG", "香港"),
            Map.entry("CNSHA", "上海"),
            Map.entry("CNSZX", "深圳"),
            Map.entry("CNPVG", "上海（浦東）"),
            Map.entry("NLRTM", "ロッテルダム"),
            Map.entry("DEHAM", "ハンブルク"),
            Map.entry("GBLON", "ロンドン"),
            Map.entry("AESHU", "シャルジャ"),
            Map.entry("KRPUS", "釜山"),
            Map.entry("TWKHH", "高雄"),
            Map.entry("MYSKG", "ポートクラン")
    );

    public Location {
        if (unlocode == null || unlocode.isBlank()) {
            throw new IllegalArgumentException("unlocode must not be blank");
        }
        if (!UNLOCODE_PATTERN.matcher(unlocode).matches()) {
            throw new IllegalArgumentException("unlocode must be 5 uppercase alphanumeric characters");
        }
    }

    public String displayName() {
        String city = CITY_NAMES.get(unlocode);
        return city != null ? city + " (" + unlocode + ")" : unlocode;
    }

    @Override
    public String toString() {
        return unlocode;
    }
}
