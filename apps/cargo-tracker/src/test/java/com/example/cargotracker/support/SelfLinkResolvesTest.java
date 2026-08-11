package com.example.cargotracker.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 同じファイルの中を指す {@code @link} が<strong>実在するものを指している</strong>こと。
 *
 * <p><strong>IT17 の品質ゲートで実際に踏んだ。</strong> R5 のコミットで
 * 「据え置きの表が空であることを検査で固定した」と書き、Javadoc にも
 * {@code {@link #据え置きの表は空である()}} と書いた。
 * <strong>ところが、その検査そのものは入っていなかった。</strong>
 * 文章だけが残り、読む人は「守られている」と信じる。
 *
 * <p>これは名簿方式の穴と同じ形である —
 * <strong>書いたつもりのものは、実行されるまで書いたことにならない。</strong>
 *
 * <p><strong>対象範囲は同じファイルの中を指す参照だけである</strong>
 * （{@code {@link #名前()}}）。他のクラスを指す参照（{@code {@link Xxx#名前()}}）は
 * 見ていない — そちらはコンパイラも見ないが、<strong>いま踏んだ形はこちらである</strong>。
 */
@DisplayName("同じファイルを指す @link が実在する")
class SelfLinkResolvesTest {

    /** {@code {@link #名前(...)}} または {@code {@link #名前}}。 */
    private static final String NAME = "[\\p{L}\\p{N}_$]+";

    private static final Pattern SELF_LINK =
            Pattern.compile("\\{@link\\s+#(" + NAME + ")\\s*(?:\\([^)]*\\))?\\s*\\}");

    /**
     * 列挙子の宣言。
     *
     * <p><strong>最後の列挙子には区切り記号が付かない。</strong> 記号を必須にすると
     * 末尾の 1 件だけが「宣言されていない」ことになる（実際に踏んだ）。
     */
    private static final Pattern ENUM_CONSTANT =
            Pattern.compile("(?m)^\\s*([A-Z][A-Z0-9_]*)\\s*(?:[,;(]|$)");

    /** メソッド・フィールドの宣言（戻り値の型は問わない）。 */
    private static final Pattern DECLARATION = Pattern.compile(
            "(?m)^\\s*(?:(?:public|private|protected|static|final|abstract"
                    + "|synchronized|default)\\s+)*[\\p{L}\\p{N}_<>\\[\\],.?\\s$]*?"
                    + "(" + NAME + ")\\s*[(;=,]");

    /**
     * <strong>存在しないものを指す {@code @link} が無い。</strong>
     *
     * <p>違反があればファイルと名前を並べて落とす。
     */
    @Test
    void 同じファイルを指すlinkは実在する() {
        List<SourceScan.SourceFile> sources = SourceScan.mainAndTest().sources();
        assertThat(sources)
                .as("走査が空なら、この検査は何も見ていない")
                .isNotEmpty();

        List<String> violations = new ArrayList<>();
        for (SourceScan.SourceFile source : sources) {
            Set<String> declared = declarationsIn(source.text());
            for (String linked : selfLinksIn(source.text())) {
                if (!declared.contains(linked)) {
                    violations.add("%s#%s".formatted(source.fileName(), linked));
                }
            }
        }

        assertThat(violations)
                .as("""
                        存在しないものを指す @link があります。

                        **書いたつもりのものは、実行されるまで書いたことになりません。**
                        IT17 で実際に踏みました — 「検査で固定した」と書きながら、
                        入っていたのは Javadoc の参照だけでした。
                        文章だけが残ると、読む人は「守られている」と信じます。""")
                .isEmpty();
    }

    /**
     * <strong>検査そのものが働くことを確かめる</strong>（メタテスト）。
     *
     * <p><strong>フィクスチャは実コードの形で作る。</strong> ここでは IT17 で実際に
     * 起きた形（検査を書いたつもりで Javadoc だけ残した）をそのまま使う。
     */
    @Test
    void 実コードの形の未解決な参照を見分けられる() {
        String missing = """
                /**
                 * <p><strong>据え置きの表は空である。</strong> 行を足すと
                 * {@link #据え置きの表は空である()} が落ちる。
                 */
                private static final Map<String, String> NOT_MEASURED_YET = new LinkedHashMap<>();

                @Test
                void 一覧を返すクエリサービスには計測がある() {
                }
                """;
        String resolved = """
                /**
                 * <p>行を足すと {@link #据え置きの表は空である()} が落ちる。
                 */
                private static final Map<String, String> NOT_MEASURED_YET = new LinkedHashMap<>();

                @Test
                void 据え置きの表は空である() {
                }
                """;

        assertThat(unresolved(missing))
                .as("実在しない参照を拾えること")
                .containsExactly("据え置きの表は空である");
        assertThat(unresolved(resolved))
                .as("実在する参照を違反にしないこと（常に落ちる検査で緑にしない）")
                .isEmpty();
    }

    private static List<String> unresolved(String source) {
        Set<String> declared = declarationsIn(source);
        return selfLinksIn(source).stream().filter(name -> !declared.contains(name)).toList();
    }

    private static Set<String> selfLinksIn(String source) {
        Set<String> links = new LinkedHashSet<>();
        Matcher matcher = SELF_LINK.matcher(source);
        while (matcher.find()) {
            links.add(matcher.group(1));
        }
        return links;
    }

    /**
     * そのファイルが宣言している名前。
     *
     * <p><strong>コメントの中は数えない。</strong> Javadoc に書いた名前を
     * 「宣言されている」と読むと、<strong>すべての参照が解決してしまう</strong>。
     */
    private static Set<String> declarationsIn(String source) {
        Set<String> names = new LinkedHashSet<>();
        String code = SourceScan.codeOf(source);
        Matcher matcher = DECLARATION.matcher(code);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        Matcher constants = ENUM_CONSTANT.matcher(code);
        while (constants.find()) {
            names.add(constants.group(1));
        }
        return names;
    }
}
