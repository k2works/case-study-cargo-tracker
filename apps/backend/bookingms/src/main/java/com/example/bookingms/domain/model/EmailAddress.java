package com.example.bookingms.domain.model;

import java.util.regex.Pattern;

/**
 * メールアドレス。
 *
 * <p>荷主の連絡先のうち、これだけが形式の不変条件を持つ（[ADR-012]）。値オブジェクトに
 * するのは、登録と編集という 2 つの入口ができたため。{@code String} のままだと、
 * 検査を通していない値と通した値が同じ型になり、入口が増えるたびに検査の書き写しが増える。
 *
 * @param value アドレスの文字列
 */
public record EmailAddress(String value) {

    /** 重複判定と連絡に使えることだけを確かめる緩い検査。厳密な妥当性は送信時にしか分からない。 */
    private static final Pattern PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** 新しく受け入れる。ここで検査する。 */
    public static EmailAddress of(String value) {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("メールアドレスの形式が不正です: " + value);
        }
        return new EmailAddress(value);
    }

    /**
     * 永続化された行から復元する。ここでは検査しない。
     *
     * <p>検査を後から足すと、その規則が無かったころの行が読めなくなる。守るのは
     * 新しく受け入れるときだけでよい。
     */
    public static EmailAddress restore(String value) {
        return value == null ? null : new EmailAddress(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
