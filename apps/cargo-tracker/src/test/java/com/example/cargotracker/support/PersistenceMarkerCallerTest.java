package com.example.cargotracker.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <strong>保存の成功を集約に反映するのはリポジトリだけである</strong>（IT14 の C6。C4）。
 *
 * <p>{@code markPersisted()} は版番号を 1 つ進める。<strong>更新が 1 件だったことを
 * 知っているのはリポジトリであり、集約は自分が保存されたかを知らない。</strong>
 *
 * <p><strong>他の層から呼べると、楽観的ロックが静かに壊れる。</strong>
 * 保存していないのに版が進めば、次の更新は「先行する更新があった」と誤って判定され、
 * <strong>正しい更新が黙って捨てられる</strong>。あるいは逆に、保存したのに版が
 * 進まなければ、2 人の更新のうち後の方が前の方を上書きする。
 * <strong>どちらも画面には何も出ない。</strong>
 *
 * <p><strong>Javadoc に「呼ぶのはリポジトリだけである」と書いてあった。</strong>
 * 書いてあっただけで、誰も確かめていなかった（IT16 の T1 で数えた 13 件のうちの 1 つ）。
 *
 * <p><strong>ArchUnit では捕まらない。</strong> ArchUnit が見るのはクラス間の依存で
 * あり、コマンドサービスは集約に依存してよい。<strong>禁じたいのは依存ではなく、
 * ある 1 つのメソッドの呼び出しである。</strong>
 */
@DisplayName("保存の反映を呼ぶのはリポジトリだけである（C4）")
class PersistenceMarkerCallerTest {

    /*
     * ソースを走査する検査は、まず自分を除く（IT16 で 3 度踏んだ）。
     * SourceScan が呼び出し元を既定で外すため、除外をここに書くことはもう無い（R8）。
     */

    /**
     * 版を進める操作。<strong>名簿にしない。</strong>
     *
     * <p>初版は {@code markPersisted()} という名前の一覧を持っていた。
     * <strong>名簿方式は載せ忘れたものほど検査から漏れる</strong>（T4 で学んだ形）。
     * 版を進める新しいメソッドを別名で足すと、それは検査の外側に生まれる。
     *
     * <p>そこで<strong>名前ではなく実体で探す</strong>。
     * {@code version++} を書いているのは集約だけであり、そこに宣言されている
     * {@code public} メソッドを版を進める操作とみなす。
     *
     * <p>形は {@code public void 名前()} である。
     */
    private static final Pattern MARKER_DECLARATION =
            Pattern.compile("public\\s+void\\s+(\\w+)\\s*\\(\\s*\\)\\s*\\{[^}]*?"
                    + "version\\s*(?:\\+\\+|\\+=\\s*1)", Pattern.DOTALL);

    /**
     * <strong>{@code markPersisted()} を呼ぶのはリポジトリの実装だけである。</strong>
     *
     * <p>違反があれば呼び出し元を並べて落とす。
     */
    @Test
    void 保存の反映を呼ぶのはリポジトリだけである() {
        List<String> markers = markerMethodNames();
        assertThat(markers)
                .as("版を進めるメソッドが 1 つも見つからないなら、検査は何も見ていない")
                .isNotEmpty();

        List<String> callers = new ArrayList<>();
        for (SourceScan.SourceFile source : SourceScan.main().sources()) {
            String text = source.code();
            boolean calls = markers.stream().anyMatch(m -> text.contains("." + m + "()"));
            if (!calls || isRepositoryImplementation(source.path())) {
                continue;
            }
            callers.add("%s（%s を呼んでいます）".formatted(source.fileName(),
                    markers.stream().filter(m -> text.contains("." + m + "()")).toList()));
        }

        assertThat(callers)
                .as("""
                        保存の反映（markPersisted）をリポジトリ以外から呼んでいます（C4）。

                        **保存していないのに版が進むと、次の正しい更新が
                        「先行する更新があった」と誤って捨てられます。**
                        画面には何も出ません。

                        版を進めてよいのは、更新が 1 件だったことを知っている
                        リポジトリだけです。""")
                .isEmpty();
    }

    /**
     * <strong>検査そのものが働くことを確かめる</strong>（メタテスト）。
     *
     * <p><strong>フィクスチャは実コードの形で作る。</strong> 「最小の違反例」だけだと、
     * メタテストが緑でも実コードの違反を見逃す（ADR-015 で学んだ形）。
     */
    @Test
    void 実コードの形の呼び出しと宣言を見分けられる() {
        String declaration = """
                /**
                 * 保存に成功したことを反映する（C13）。
                 *
                 * <p><strong>呼ぶのはリポジトリだけである。</strong>
                 */
                public void markPersisted() {
                    this.version++;
                }
                """;
        String invocation = """
                @Override
                public boolean update(Invoice invoice) {
                    int updated = mapper.update(toRecord(invoice));
                    if (updated == 1) {
                        invoice.markPersisted();
                    }
                    return updated == 1;
                }
                """;

        assertThat(declaresMarker(declaration))
                .as("宣言を呼び出しと数えないこと（集約自身は違反ではない）")
                .isTrue();
        assertThat(declaresMarker(invocation))
                .as("呼び出しを宣言と取り違えないこと")
                .isFalse();
    }

    /**
     * 版を進めるメソッドの名前（集約の宣言から導く）。
     *
     * <p><strong>名簿にしない。</strong> 名前で持つと、別名で足したものが漏れる。
     */
    private static List<String> markerMethodNames() {
        List<String> names = new ArrayList<>();
        for (SourceScan.SourceFile source : SourceScan.main().sources()) {
            Matcher matcher = MARKER_DECLARATION.matcher(source.code());
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
        return names;
    }

    /** 集約自身の宣言を含むか（呼び出しと取り違えないため）。 */
    private static boolean declaresMarker(String source) {
        return MARKER_DECLARATION.matcher(source).find();
    }

    /**
     * リポジトリの実装か。
     *
     * <p><strong>パッケージで判定する。</strong> 名前で判定すると、
     * {@code *Repository} を名乗らない実装が素通りする。
     */
    private static boolean isRepositoryImplementation(Path source) {
        return source.toString().replace('\\', '/').contains("/infrastructure/repositories/");
    }

}
