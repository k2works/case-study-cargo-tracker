package com.example.cargotracker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 列の多い一覧は<strong>狭い画面でも読める</strong>（IT17 の R7）。
 *
 * <p>IT16 のクローズ前レビュー M1：請求書一覧が種別を足して <strong>9 列</strong>に
 * なったが、<strong>狭い画面で読めるかを確かめていなかった</strong>。
 *
 * <p>Bootstrap の表は、幅が足りないと列を潰して折り返す。9 列では
 * <strong>金額が縦に割れ、日付が 2 行になる</strong>。読めるつもりで読めていない状態は、
 * 空欄よりたちが悪い —<strong>読み違えたことに気づけない。</strong>
 *
 * <p><strong>列は隠さない。</strong> 経理担当者が請求書を突き合わせるとき、
 * どの列が要るかは場面で変わる。狭い画面で列を落とすと、
 * <strong>その場面に当たった人だけが仕事を進められなくなる</strong>。
 * 横に送る（{@code table-responsive}）ことで、情報を落とさずに読めるようにする。
 *
 * <p><strong>閾値は 6 列とする。</strong> 一般的な携帯の幅で、日本語の見出しと
 * 値が折り返さずに並ぶ上限である。
 *
 * <p><strong>対象範囲は {@code <thead>} を持つ表だけである。</strong> 詳細画面の
 * 「項目名 / 値」の表は行ごとに {@code <th>} を置くが、横幅は 2 列であり
 * 横に送る必要が無い（{@code tracking/exception-detail.html} は {@code <th>} が
 * 12 個あるが 2 列である）。<strong>見出し行を {@code <thead>} で書かない一覧を
 * 作ると、この検査からは外れる</strong> — 数え方が変わったときに気づけるよう、
 * 見落としではなく決めごととしてここに書く。
 */
@DisplayName("列の多い一覧は狭い画面でも読める（R7）")
class WideTableReadabilityTest {

    /** これを超えたら横に送れるようにする。 */
    private static final int MAX_COLUMNS_WITHOUT_SCROLL = 6;

    private static final Pattern HEADER_ROW =
            Pattern.compile("<thead>(.*?)</thead>", Pattern.DOTALL);
    private static final Pattern COLUMN = Pattern.compile("<th[\\s>]");

    /**
     * <strong>列の多い表は横に送れる。</strong>
     *
     * <p>違反があればテンプレート名と列数を並べて落とす。
     */
    @Test
    void 列の多い一覧は横に送れる() throws IOException {
        List<Path> templates = templates();
        assertThat(templates)
                .as("テンプレートが 1 つも見つからないなら、検査は何も見ていない")
                .isNotEmpty();

        List<String> violations = new ArrayList<>();
        for (Path template : templates) {
            String html = Files.readString(template);
            int columns = widestHeader(html);
            if (columns > MAX_COLUMNS_WITHOUT_SCROLL && !html.contains("table-responsive")) {
                violations.add("%s（%d 列）".formatted(template.getFileName(), columns));
            }
        }

        assertThat(violations)
                .as("""
                        列の多い一覧が狭い画面で潰れます（IT17 の R7）。

                        **金額が縦に割れ、日付が 2 行になります。**
                        読めるつもりで読めていない状態は、空欄よりたちが悪い —
                        読み違えたことに気づけません。

                        表を <div class="table-responsive"> で包んでください。
                        **列は隠さないこと。** どの列が要るかは場面で変わります。""")
                .isEmpty();
    }

    /**
     * <strong>検査そのものが働くことを確かめる</strong>（メタテスト）。
     *
     * <p><strong>フィクスチャは実コードの形で作る。</strong> 「最小の違反例」だけだと、
     * メタテストが緑でも実コードの違反を見逃す。
     */
    @Test
    void 実コードの形の表を数えられる() {
        String nineColumns = """
                <table class="table table-sm align-middle" th:unless="${invoices.isEmpty()}">
                  <thead>
                    <tr><th>請求番号</th><th>種別</th><th>荷主</th><th>追跡番号</th><th>請求総額</th>
                        <th>発行日</th><th>支払期限</th><th>料金の状態</th><th>支払いの状態</th></tr>
                  </thead>
                </table>
                """;
        String threeColumns = """
                <table class="table">
                  <thead><tr><th>航海番号</th><th>船名</th><th>出発地</th></tr></thead>
                </table>
                """;

        assertThat(widestHeader(nineColumns)).isEqualTo(9);
        assertThat(widestHeader(threeColumns))
                .as("列の少ない表を巻き込まないこと（常に落ちる検査で緑にしない）")
                .isEqualTo(3);
    }

    /** そのテンプレートで最も列の多いヘッダの列数。 */
    private static int widestHeader(String html) {
        int widest = 0;
        Matcher headers = HEADER_ROW.matcher(html);
        while (headers.find()) {
            Matcher columns = COLUMN.matcher(headers.group(1));
            int count = 0;
            while (columns.find()) {
                count++;
            }
            widest = Math.max(widest, count);
        }
        return widest;
    }

    private static List<Path> templates() throws IOException {
        try (Stream<Path> paths = Files.walk(templateRoot())) {
            return paths.filter(p -> p.toString().endsWith(".html")).sorted().toList();
        }
    }

    private static Path templateRoot() {
        Path current = Path.of("").toAbsolutePath();
        for (int i = 0; i < 5 && current != null; i++) {
            Path candidate = current.resolve("src/main/resources/templates");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            candidate = current.resolve("apps/cargo-tracker/src/main/resources/templates");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("テンプレートのディレクトリが見つかりません");
    }
}
