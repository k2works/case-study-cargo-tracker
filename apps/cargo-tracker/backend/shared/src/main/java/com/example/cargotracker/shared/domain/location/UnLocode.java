package com.example.cargotracker.shared.domain.location;

import java.util.regex.Pattern;

/**
 * UN/LOCODE（domain-model.md「Shared Kernel」）。
 *
 * <p>ISO 3166-1 の国コード 2 文字 + 港湾コード 3 文字。全 BC が同じ意味で使う。</p>
 */
public record UnLocode(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[A-Z]{5}$");

    public UnLocode {
        if (value == null || !FORMAT.matcher(value).matches()) {
            // 小文字を通すと、同じ港が 2 通りの書き方で入り、一覧の突き合わせが合わなくなる。
            throw new IllegalArgumentException("UN/LOCODE は英大文字 5 文字です: " + value);
        }
    }

    /** 先頭 2 文字。輸出免税の判定（Billing）が使う。 */
    public CountryCode countryCode() {
        return new CountryCode(value.substring(0, 2));
    }

    @Override
    public String toString() {
        return value;
    }
}
