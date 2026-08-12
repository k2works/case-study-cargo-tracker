package com.example.cargotracker.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <strong>ソースを走査する検査の土台</strong>（IT17 の R8 / R9）。
 *
 * <p>IT16 でソースを走査する検査を 5 本足し、<strong>そのうち 3 本で
 * 「検査が自分のフィクスチャを違反として数える」を踏んだ</strong>。3 度とも
 * 症状が出てから {@code SELF} や {@code EXCLUDED} を書き足して直しており、
 * <strong>書き忘れれば必ず起きる形が 10 本すべてに残っていた。</strong>
 *
 * <p>そこで<strong>自分の除外を既定にする</strong>。呼び出し元のテストクラスは
 * 明示しなくても走査対象から外れる — <strong>書き忘れても起きない。</strong>
 *
 * <p>あわせて {@link SourceScan.SourceFile#code()} が<strong>コメントと文字列
 * リテラルを走査対象から外す</strong>（R9）。「〜しない」と書いたコメントを
 * 違反として拾う空振りを防ぐ。ただし SQL のように<strong>文字列リテラルの中身
 * こそが検査対象</strong>である検査もあるため、{@link SourceScan.SourceFile#text()}
 * を選べるようにし、既定にはしない。
 */
@DisplayName("ソース走査の支援クラス（R8 / R9）")
class SourceScanTest {

    /**
     * <strong>呼び出したテスト自身は既定で走査対象から外れる。</strong>
     *
     * <p>IT16 で 3 度踏んだ形を、支援クラスの既定でひとつ残らず消す。
     */
    @Test
    void 呼び出し元のテスト自身は既定で除外される() throws IOException {
        List<String> names = SourceScan.test().files().stream()
                .map(p -> p.getFileName().toString())
                .toList();

        assertThat(names)
                .as("走査が空なら、この検査は何も見ていない（IT17 の R8）")
                .isNotEmpty();
        assertThat(names)
                .as("呼び出し元のテスト自身は明示しなくても外れること")
                .doesNotContain("SourceScanTest.java");
        assertThat(names)
                .as("他のテストは走査対象に残ること（除外が広すぎない）")
                .contains("NarrowParseCatchTest.java");
    }

    /** <strong>自分以外の除外も足せる。</strong> 支援クラス自身のように、正当な例外は明示する。 */
    @Test
    void 除外を明示的に足せる() throws IOException {
        List<String> names = SourceScan.test()
                .excluding("NarrowParseCatchTest.java")
                .files().stream()
                .map(p -> p.getFileName().toString())
                .toList();

        assertThat(names).doesNotContain("NarrowParseCatchTest.java");
        assertThat(names).contains("CargoFixtureOwnershipTest.java");
    }

    /**
     * <strong>コメントと文字列リテラルは {@code code()} に現れない</strong>（R9）。
     *
     * <p>行番号は保たれる。違反の場所を指せなくなると、検査は「どこか」しか言えない。
     */
    @Test
    void コメントと文字列リテラルを外しても行番号は保たれる() {
        String source = """
                class Sample {
                    // INSERT INTO sample_row と書いてあるだけのコメント
                    /* INSERT INTO sample_row を含むブロックコメント */
                    String sql = "INSERT INTO sample_row (booking_id) VALUES (?)";
                    void run() {
                        jdbc.update(sql);
                    }
                }
                """;

        String code = SourceScan.codeOf(source);

        assertThat(code)
                .as("コメントと文字列リテラルの中身が残らないこと")
                .doesNotContain("INSERT INTO sample_row");
        assertThat(code)
                .as("コードは残ること")
                .contains("jdbc.update(sql)");
        assertThat(code.lines().count())
                .as("行番号がずれないこと（IT17 の R8）")
                .isEqualTo(source.lines().count());
        assertThat(code.lines().toList().get(5))
                .as("同じ行に同じ内容が残ること")
                .contains("jdbc.update(sql)");
    }

    /**
     * <strong>テキストブロックの中身も外す。</strong>
     *
     * <p>メタテストのフィクスチャはテキストブロックで書く。ここを外せないと、
     * <strong>検査が自分のフィクスチャを拾う</strong>形が除外の書き忘れとは別経路で戻る。
     */
    @Test
    void テキストブロックの中身も外れる() {
        String source = """
                String fixture = \"""
                        INSERT INTO sample_row (booking_id)
                        VALUES (?)
                        \""";
                int x = 1;
                """;

        String code = SourceScan.codeOf(source);

        assertThat(code).doesNotContain("INSERT INTO sample_row");
        assertThat(code).contains("int x = 1;");
        assertThat(code.lines().count()).isEqualTo(source.lines().count());
    }

    /** <strong>正しい形を壊さない。</strong> 文字も除算もコメントの開始に見えてはならない。 */
    @Test
    void 文字リテラルと除算を誤って外さない() {
        String source = """
                char slash = '/';
                int half = total / 2;
                """;

        String code = SourceScan.codeOf(source);

        assertThat(code).contains("int half = total / 2;");
        assertThat(code.lines().count()).isEqualTo(source.lines().count());
    }
}
