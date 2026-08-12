package com.example.cargotracker.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 検査の失敗メッセージは<strong>由来を持つ</strong>（IT17 の Try T3 / 引き継ぎ C2）。
 *
 * <p>IT17 のクローズ前レビューで architect が「検査失敗時のメッセージから ADR や
 * 由来に辿り着けるか未検証」と述べ、<strong>実測したところ 39 件中 29 件に
 * 由来が無かった</strong>。
 *
 * <p><strong>失敗を見た人は、まずメッセージしか読まない。</strong> 「マッパーが
 * 他 BC のテーブルを触っています」と言われても、<strong>なぜそれが駄目なのか</strong>は
 * ADR-015 を開かないと分からない。番号が書いてあれば 1 手で辿れる。
 *
 * <p><strong>対象は走査する検査だけである</strong>（{@link SourceScan} を使うもの）。
 * 業務のテストは受入基準そのものが由来であり、番号を書く先が無い。
 *
 * <p><strong>この検査は「育つ負債」を止める</strong>（IT16 の教訓）。検査を 1 本
 * 足すたびにメッセージが増えるため、放置すると増え続ける。
 */
@DisplayName("検査の失敗メッセージは由来を持つ（C2）")
class CheckMessageOriginTest {

    /** ADR 番号（{@code ADR-015}）または指摘番号（{@code IT16 の C3}）。 */
    private static final Pattern ORIGIN = Pattern.compile("ADR-\\d|IT\\d+ の [A-Za-z]?\\d");

    /** {@code .as(...)} に続けてアサートしている箇所。 */
    private static final Pattern ASSERTION_MESSAGE = Pattern.compile(
            "\\.as\\((.{0,900}?)\\)\\s*\\n\\s*\\.(isEmpty|isNotEmpty|containsExactly\\w*"
                    + "|isTrue|isFalse|isEqualTo|isPositive|isNotNegative)\\(",
            Pattern.DOTALL);

    /**
     * <strong>走査する検査の失敗メッセージに由来がある。</strong>
     *
     * <p>違反があればファイルと本数を並べて落とす。
     */
    @Test
    void 走査する検査の失敗メッセージは由来を持つ() {
        List<SourceScan.SourceFile> checks = SourceScan.test().sources().stream()
                .filter(source -> source.text().contains("SourceScan."))
                .filter(source -> !source.fileName().equals("SourceScan.java"))
                .toList();

        assertThat(checks)
                .as("走査する検査が 1 本も見つからないなら、この検査は何も見ていない")
                .isNotEmpty();

        List<String> violations = new ArrayList<>();
        for (SourceScan.SourceFile check : checks) {
            int lacking = messagesWithoutOrigin(check.text());
            if (lacking > 0) {
                violations.add("%s（%d 件）".formatted(check.fileName(), lacking));
            }
        }

        assertThat(violations)
                .as("""
                        検査の失敗メッセージに由来がありません（IT17 の Try T3 / C2）。

                        **失敗を見た人は、まずメッセージしか読みません。**
                        「なぜそれが駄目なのか」は ADR を開かないと分からず、
                        番号が書いていなければ、どの ADR かも分かりません。

                        ADR 番号（ADR-015）か指摘番号（IT16 の C3）を入れてください。""")
                .isEmpty();
    }

    /**
     * <strong>検査そのものが働くことを確かめる</strong>（メタテスト）。
     *
     * <p><strong>フィクスチャは実コードの形で作る。</strong> ここでは IT17 の実測で
     * 由来が無かった実際のメッセージを使う。
     */
    @Test
    void 由来の有無を見分けられる() {
        String without = """
                        assertThat(stale)
                                .as("計測を書いたクエリサービスが据え置きの表に残っています")
                                .isEmpty();
                """;
        String with = """
                        assertThat(stale)
                                .as("計測済みが据え置きの表に残っています（IT17 の R5）")
                                .isEmpty();
                """;
        String withAdr = """
                        assertThat(violations)
                                .as("マッパーが他 BC のテーブルを触っています（ADR-015）")
                                .isEmpty();
                """;

        assertThat(messagesWithoutOrigin(without))
                .as("由来の無いメッセージを拾えること")
                .isEqualTo(1);
        assertThat(messagesWithoutOrigin(with))
                .as("指摘番号を由来と認めること（常に落ちる検査で緑にしない）")
                .isZero();
        assertThat(messagesWithoutOrigin(withAdr))
                .as("ADR 番号を由来と認めること")
                .isZero();
    }

    /** 由来を持たない失敗メッセージの数。 */
    private static int messagesWithoutOrigin(String source) {
        int lacking = 0;
        Matcher matcher = ASSERTION_MESSAGE.matcher(source);
        while (matcher.find()) {
            if (!ORIGIN.matcher(matcher.group(1)).find()) {
                lacking++;
            }
        }
        return lacking;
    }
}
