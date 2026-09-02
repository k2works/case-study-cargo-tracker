package com.example.shared.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 港湾・地点を表す共有カーネルの値オブジェクト。
 *
 * <p>UN/LOCODE（国連ロケーションコード）で識別する。全バウンデッドコンテキストが
 * 同一の定義を共有する唯一の型であり、これ以外の概念は各コンテキスト固有型として定義する。
 */
public final class Location {

    /** UN/LOCODE は国コード 2 文字 + 地点コード 3 文字の計 5 文字。 */
    private static final Pattern UN_LOCODE_PATTERN = Pattern.compile("^[A-Z]{2}[A-Z0-9]{3}$");

    private final String unLocode;
    private final String name;

    private Location(String unLocode, String name) {
        this.unLocode = unLocode;
        this.name = name;
    }

    /**
     * UN/LOCODE と名称から地点を生成する。
     *
     * @param unLocode UN/LOCODE（例: JPTYO）
     * @param name 地点名称（例: Tokyo）
     * @return 地点
     * @throws IllegalArgumentException UN/LOCODE の形式が不正、または名称が空の場合
     */
    public static Location of(String unLocode, String name) {
        if (unLocode == null || !UN_LOCODE_PATTERN.matcher(unLocode).matches()) {
            throw new IllegalArgumentException("UN/LOCODE の形式が不正です: " + unLocode);
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("地点名称は必須です");
        }
        return new Location(unLocode, name);
    }

    public String unLocode() {
        return unLocode;
    }

    public String name() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Location location)) {
            return false;
        }
        return unLocode.equals(location.unLocode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(unLocode);
    }

    @Override
    public String toString() {
        return name + " (" + unLocode + ")";
    }
}
