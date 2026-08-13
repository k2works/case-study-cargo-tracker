package com.example.cargotracker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR の守り手は<strong>何を見ているかまで書く</strong>（IT16 のふりかえり T4）。
 *
 * <p>IT16 で全 ADR に「何がどこで守るか」の表を足したところ、
 * <strong>宣言はされているが検査の手が届いていない</strong>規則が 3 件見つかった。
 *
 * <ul>
 *   <li>ADR-009 の規則 1（{@code AFTER_COMMIT}）— 既存の検査は
 *       {@code @TransactionalEventListener} を書いたファイルしか見ておらず、
 *       素の {@code @EventListener} に戻しても緑だった</li>
 *   <li>Checkstyle の {@code ParameterNumber} — <strong>レコードを見ない</strong>。
 *       35 要素のレコードが 1 件も指摘されなかった</li>
 *   <li>ADR-003 — 継承だけを見ており、<strong>基底の中身を H2 に差し替えると全件が素通り</strong>する</li>
 * </ul>
 *
 * <p>いずれも<strong>守り手の名前は書かれていた</strong>。欠けていたのは
 * <strong>その守り手が何を見ているか</strong>である。名前だけの表は
 * 「守られている」と読ませてしまう — <strong>名簿方式の穴と同じ形である。</strong>
 *
 * <p>そこで表に<strong>「対象範囲」の欄</strong>を設け、
 * <strong>空欄を赤にする</strong>。守り手が無い規則は「—」ではなく、
 * <strong>何が守られないかを書く</strong>。
 */
@DisplayName("ADR の守り手は対象範囲まで書く（IT16 の T4）")
class AdrGuardScopeTest {

    private static final String HEADING = "## 何がどこで守るか";
    private static final String SCOPE_COLUMN = "対象範囲";

    /**
     * <strong>すべての ADR が「何がどこで守るか」を持つ。</strong>
     *
     * <p>決定を書いて守り手を書かない ADR は、<strong>守られていることの根拠を
     * 読み手の想像に委ねる</strong>。
     */
    @Test
    void すべてのADRが守り手の表を持つ() throws IOException {
        List<Path> adrs = adrDocuments();
        assertThat(adrs)
                .as("ADR が 1 件も見つからないなら、検査は何も見ていない")
                .isNotEmpty();

        List<String> missing = new ArrayList<>();
        for (Path adr : adrs) {
            if (!Files.readString(adr).contains(HEADING)) {
                missing.add(adr.getFileName().toString());
            }
        }

        assertThat(missing)
                .as("ADR に「" + HEADING + """
                        」の節がありません。

                        **決定を書いて守り手を書かないと、守られていることの根拠が
                        読み手の想像に委ねられます。**
                        守り手が無い規則は「守らない」と、何が守られないかを書いてください。""")
                .isEmpty();
    }

    /**
     * <strong>守り手の表は「対象範囲」の欄を持つ。</strong>
     *
     * <p>守り手の名前だけでは、<strong>その検査が何を見ていないか</strong>が分からない。
     * IT16 で見つかった 3 件は、いずれも名前は書かれていた。
     */
    @Test
    void 守り手の表は対象範囲の欄を持つ() throws IOException {
        List<String> missing = new ArrayList<>();
        for (Path adr : adrDocuments()) {
            List<String> table = guardTable(Files.readString(adr));
            if (table.isEmpty()) {
                continue;
            }
            if (!table.getFirst().contains(SCOPE_COLUMN)) {
                missing.add(adr.getFileName().toString());
            }
        }

        assertThat(missing)
                .as("守り手の表に「" + SCOPE_COLUMN + """
                        」の欄がありません（IT16 の T4）。

                        **守り手の名前だけでは、その検査が何を見ていないかが分かりません。**
                        IT16 で見つかった 3 件は、いずれも名前は書かれていました。""")
                .isEmpty();
    }

    /**
     * <strong>対象範囲の欄を空にしない。</strong>
     *
     * <p>欄だけ作って空にすると、<strong>欄があること自体が「書いた」に見える</strong>。
     */
    @Test
    void 対象範囲の欄を空にしない() throws IOException {
        List<String> blanks = new ArrayList<>();
        for (Path adr : adrDocuments()) {
            List<String> table = guardTable(Files.readString(adr));
            for (int i = 2; i < table.size(); i++) {
                List<String> cells = cellsOf(table.get(i));
                if (cells.size() < 3 || cells.get(2).isBlank() || cells.get(2).equals("—")) {
                    blanks.add("%s: %s".formatted(adr.getFileName(),
                            cells.isEmpty() ? table.get(i) : cells.getFirst()));
                }
            }
        }

        assertThat(blanks)
                .as("守り手の表の「" + SCOPE_COLUMN + """
                        」が空欄です（IT16 の T4）。

                        **欄だけ作って空にすると、欄があること自体が「書いた」に見えます。**
                        守り手が無い規則にも、何が検査の外にあるのかを書いてください。""")
                .isEmpty();
    }

    /** 「何がどこで守るか」節の表の行（ヘッダと区切りを含む）。 */
    private static List<String> guardTable(String document) {
        int from = document.indexOf(HEADING);
        if (from < 0) {
            return List.of();
        }
        List<String> rows = new ArrayList<>();
        for (String line : document.substring(from).lines().toList()) {
            if (line.startsWith("|")) {
                rows.add(line);
            } else if (!rows.isEmpty() && !line.isBlank()) {
                break;
            }
        }
        return rows;
    }

    /** 表の行のセル。 */
    private static List<String> cellsOf(String row) {
        String trimmed = row.strip();
        String body = trimmed.substring(1, trimmed.length() - (trimmed.endsWith("|") ? 1 : 0));
        return Stream.of(body.split("\\|", -1)).map(String::strip).toList();
    }

    /** ADR の本文（索引は決定ではない）。 */
    private static List<Path> adrDocuments() throws IOException {
        try (Stream<Path> paths = Files.list(adrDirectory())) {
            return paths
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .filter(p -> !p.getFileName().toString().equals("index.md"))
                    .sorted()
                    .toList();
        }
    }

    private static Path adrDirectory() {
        Path current = Path.of("").toAbsolutePath();
        for (int i = 0; i < 5 && current != null; i++) {
            Path candidate = current.resolve("docs/adr");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("ADR のディレクトリが見つかりません");
    }
}
