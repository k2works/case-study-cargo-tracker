package com.example.cargotracker.shipper.domain.model.valueobjects;

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

    /**
     * メールアドレスの形式。
     *
     * <p><strong>ドメイン部のラベルからドットを除いているのは、後戻り（バックトラック）を
     * 起こさないためである。</strong> {@code [^@\\s]+\\.[^@\\s]+} は「どこで区切るか」が
     * 一意に決まらず、ドットの無い長い入力（{@code a@bbbb...b}）に対して照合が
     * 入力長の 2 乗に比例して遅くなる。**入力欄に長い文字列を貼るだけで CPU を
     * 消費させられる**（ReDoS）。ラベルにドットを含めない形にすると区切りが一意に決まり、
     * 照合は入力長に比例する。
     *
     * <p>あわせて {@code a@b..c} のような連続したドットを弾くようになった。
     * 元の形では通っていたが、メールアドレスとして正しくない。
     *
     * <p>繰り返しに上限（10）を置いているのは、**上限の無い繰り返しが長い入力に対して
     * スタックを消費する**ためである（SonarQube java:S5998）。実在のメールアドレスの
     * ドメインは 10 ラベルに遠く及ばない。
     */
    private static final Pattern FORMAT =
            Pattern.compile("^[^@\\s]+@[^@\\s.]+(?:\\.[^@\\s.]+){1,10}$");
    private static final int MAX_LENGTH = 200;

    public Email {
        if (value == null) {
            throw new IllegalArgumentException("メールアドレスは必須です");
        }
        // **長さの検査を形式の検査より先に行う。** 逆にすると、上限をはるかに超える
        // 入力（数万文字）がそのまま正規表現に渡る。長さで先に落とせば、
        // 照合の対象は必ず 200 文字以内に収まる。
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("メールアドレスは " + MAX_LENGTH + " 文字以内です");
        }
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("メールアドレスの形式が不正です: " + value);
        }
    }
}
