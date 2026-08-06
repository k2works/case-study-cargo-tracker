package com.example.cargotracker.shipper.domain.model;

import java.util.regex.Pattern;

/**
 * メールアドレス。
 *
 * <p>一意性は DB の UNIQUE 制約が最後の防波堤である（US02）。画面の非同期チェックは
 * 同時登録の競合に対して無力なため、ここでは形式のみを守る。
 *
 * @param value メールアドレス
 */
public record Email(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MAX_LENGTH = 200;

    public Email {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("メールアドレスの形式が不正です: " + value);
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("メールアドレスは " + MAX_LENGTH + " 文字以内です");
        }
    }
}
