package com.example.cargotracker.routing.domain.model;

import java.util.regex.Pattern;

/**
 * 航海番号。Routing Context 固有の航海識別子。
 *
 * <p><strong>共有カーネルに置かない</strong>（ADR-005）。他の BC から参照する場合は
 * ACL ポートを経由する。船会社が採番する業務コードであり、書式は会社ごとに異なる。
 *
 * @param value 英数字とハイフン（前後の空白は取り除く）
 */
public record VoyageNumber(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9-]{1,20}");

    public VoyageNumber {
        if (value == null) {
            throw new IllegalArgumentException("航海番号は必須です");
        }
        value = value.strip();
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "航海番号は英数字とハイフンで 20 文字までです: " + value);
        }
    }
}
