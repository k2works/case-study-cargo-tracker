package com.example.bookingms.domain.model;

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

    /**
     * アドレスの上限（RFC 5321）。
     *
     * <p>入力は荷主の登録・編集から届く<strong>外部からの値</strong>である。長さを縛らないと、
     * 長さに比例した仕事が入口の数だけ積み上がる。
     */
    private static final int MAX_LENGTH = 254;

    /** 新しく受け入れる。ここで検査する。 */
    public static EmailAddress of(String value) {
        if (!isAcceptable(value)) {
            throw new IllegalArgumentException("メールアドレスの形式が不正です: " + value);
        }
        return new EmailAddress(value);
    }

    /**
     * 重複判定と連絡に使えることだけを確かめる緩い検査。厳密な妥当性は送信時にしか分からない。
     *
     * <p><strong>正規表現を使わない。</strong>この形（{@code [^@\s]+@[^@\s]+\.[^@\s]+}）は
     * ドメイン側の {@code [^@\s]} が {@code .} を含むため、区切りの取り方が一意に決まらない。
     * 一致しない長い入力では区切りの候補を総当たりし、<strong>長さの 2 乗に比例する時間</strong>
     * がかかる（SonarQube の指摘）。曖昧さを消そうと {@code (?:\.[^@\s.]+)+} のような形にすると、
     * 今度は Java の正規表現がループを再帰で回すため、<strong>数千文字で StackOverflowError</strong>
     * になる（実測）。どちらも外部からの入力で起こせる。
     *
     * <p>確かめているのは元の正規表現と同じ 3 点である。
     * <ul>
     *   <li>空白を含まない
     *   <li>{@code @} がちょうど 1 つあり、その前後が空でない
     *   <li>{@code @} より後ろに {@code .} があり、その前後が空でない
     * </ul>
     */
    private static boolean isAcceptable(String value) {
        if (value == null || value.length() > MAX_LENGTH) {
            return false;
        }
        if (containsWhitespace(value)) {
            return false;
        }
        int at = value.indexOf('@');
        // 先頭の @ は前が空。2 つ以上の @ は、元の正規表現も受け付けない
        if (at <= 0 || at != value.lastIndexOf('@')) {
            return false;
        }
        int dot = value.indexOf('.', at + 1);
        // ドットは @ の直後でも末尾でもない（前後が空でないこと）
        return dot > at + 1 && dot < value.length() - 1;
    }

    private static boolean containsWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
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
