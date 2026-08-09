package com.example.cargotracker.booking.domain.model;

import java.util.Locale;
import java.util.random.RandomGenerator;

/**
 * 引取確認コード（US35）。
 *
 * <p>IT7 の引取記録は<strong>提示された値をそのまま書き写すだけ</strong>で、
 * 照合する相手がシステムの中に無かった。<strong>記録はできるが証明にならない。</strong>
 *
 * <p><strong>追跡番号とは別の値である。</strong> 追跡番号は荷主が取引先へ転送する
 * ことを前提にした「合鍵」であり（公開追跡は認証を持たない相手に見せる）、
 * <strong>それを知っているだけで引き取れてはならない</strong>。
 *
 * <p><strong>形式も別にする。</strong> {@code TRK-} に似せると、現場が取り違えて
 * 追跡番号を入力する。<strong>入れられる形にしておいて「入れるな」と教育するのは、
 * 仕組みではない。</strong>
 *
 * <p><strong>採番は予測できない値にする。</strong> 追跡番号は日付＋連番であり
 * 推測できる形をしている。同じ作り方にすると、1 つ知るだけで隣の貨物も引き取れる。
 */
public record ClaimCode(String value) {

    /** {@code CLM-} ＋ 8 桁（数字と大文字）。**追跡番号と見分けがつく形にする。** */
    private static final java.util.regex.Pattern FORMAT =
            java.util.regex.Pattern.compile("CLM-[0-9A-Z]{8}");

    /** 採番に使う文字。**紛らわしい文字を外す** — 電話・紙で伝わる値である。 */
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private static final int LENGTH = 8;

    public ClaimCode {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "引取確認コードは CLM- に続く 8 桁で指定してください: " + value);
        }
    }

    /** 既存の値から作る（復元・入力の読み取り）。 */
    public static ClaimCode of(String value) {
        return new ClaimCode(value == null ? null : value.strip().toUpperCase(Locale.ROOT));
    }

    /**
     * 新しく採番する。
     *
     * <p><strong>乱数生成器を受け取る。</strong> 内部で作ると、テストから
     * 採番の性質（重複しないこと）を確かめられない。
     */
    public static ClaimCode issue(RandomGenerator random) {
        StringBuilder builder = new StringBuilder("CLM-");
        for (int i = 0; i < LENGTH; i++) {
            builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return new ClaimCode(builder.toString());
    }

    /**
     * 提示された値と一致するか。
     *
     * <p><strong>大小文字と前後の空白は問わない。</strong> コードは電話や紙で伝わる。
     * 入力の揺れで拒むと、正しい荷受人が引き取れず、
     * <strong>現場は照合そのものを迂回したくなる</strong>。
     *
     * <p>空・{@code null}・形式違いは<strong>不一致</strong>である。
     * 例外にすると、呼び出し側が「照合していない」経路を作りやすい。
     */
    public boolean matches(String presented) {
        if (presented == null || presented.isBlank()) {
            return false;
        }
        return value.equals(presented.strip().toUpperCase(Locale.ROOT));
    }
}
