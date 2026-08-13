package com.example.cargotracker.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * <strong>{@code data-model.md} が書いている列が、実際のスキーマに存在すること。</strong>
 *
 * <p>IT9 で危険物・冷凍の 6 列を追加したとき、設計ドキュメントには 8 イテレーション前から
 * その列が書かれていた。<strong>テーブルはあるが列が無い</strong>という形の乖離は、
 * マイグレーションを一覧しても気づけない。IT10 でも同じ形が再発している
 * （{@code tracking_exception_event} に発生場所の列が無い）。
 *
 * <p>この乖離が高くつくのは、<strong>計画が設計ドキュメントを読んで立つ</strong>からである。
 * 「列はもうある」という前提でタスクを見積もり、実装の途中でマイグレーションが
 * 要ることに気づく。<strong>2 回続いた見落としは、次も起きる。</strong>
 *
 * <p>検査は<strong>片方向である</strong>。ドキュメントに書かれた列が実在することだけを見る。
 * 逆（スキーマにあるがドキュメントに無い）は、<strong>まだ書いていないだけ</strong>の
 * 段階が正常にありうるため、ここでは落とさない。
 *
 * <p>型・制約は見ない。それは移植性と方言の問題であり、
 * {@link H2DialectSmokeTest} が別の角度から受け持つ。
 */
@SpringBootTest
@ActiveProfiles("h2-dialect")
@DisplayName("data-model.md の列が実スキーマに存在すること")
class DataModelDocumentSchemaTest {

    /** ER 図のエンティティ宣言（例: entity "cargo（貨物）" as cargo で始まり波括弧で開く行）。 */
    private static final Pattern ENTITY =
            Pattern.compile("^entity\\s+\"?([a-z_][a-z0-9_]*)");

    /** 列の宣言。{@code * booking_id : UUID <<UK, NOT NULL>>} */
    private static final Pattern COLUMN =
            Pattern.compile("^\\*?\\s*([a-z_][a-z0-9_]*)\\s*:");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void ドキュメントが書いている列はすべて実在する() throws IOException {
        Map<String, Set<String>> documented = 設計ドキュメントの列();
        assertThat(documented)
                .as("data-model.md から ER 図のテーブルを読めていること")
                .hasSizeGreaterThan(10);

        List<String> gaps = new ArrayList<>();
        for (var table : documented.entrySet()) {
            Set<String> actual = 実スキーマの列(table.getKey());
            if (actual.isEmpty()) {
                gaps.add("テーブルが無い: %s".formatted(table.getKey()));
                continue;
            }
            for (String column : table.getValue()) {
                if (!actual.contains(column)) {
                    gaps.add("列が無い: %s.%s".formatted(table.getKey(), column));
                }
            }
        }

        assertThat(gaps)
                .as("""
                        data-model.md に書かれている列が実スキーマにありません。
                        **ドキュメントを直すか、マイグレーションを足すかは中身次第です。**
                        列名を変えたのにドキュメントが古いなら前者、
                        書いたつもりで作っていないなら後者です。""")
                .isEmpty();
    }

    /**
     * {@code data-model.md} の PlantUML ER 図からテーブルと列を読む。
     *
     * <p>同じテーブルが概要図と論理データモデル図の両方に現れる。
     * <strong>両方を合わせて 1 つの集合にする</strong> — どちらに書かれていても
     * 「ドキュメントが約束した列」であることに変わりはない。
     */
    private static Map<String, Set<String>> 設計ドキュメントの列() throws IOException {
        String text = Files.readString(設計ドキュメント());
        Map<String, Set<String>> tables = new LinkedHashMap<>();
        String current = null;
        for (String raw : text.lines().toList()) {
            String line = raw.strip();
            Matcher entity = ENTITY.matcher(line);
            if (entity.find() && line.endsWith("{")) {
                current = entity.group(1);
                tables.computeIfAbsent(current, key -> new LinkedHashSet<>());
                continue;
            }
            if ("}".equals(line)) {
                current = null;
                continue;
            }
            if (current == null || "--".equals(line) || line.isEmpty()) {
                continue;
            }
            Matcher column = COLUMN.matcher(line);
            if (column.find()) {
                tables.get(current).add(column.group(1));
            }
        }
        return tables;
    }

    /**
     * 設計ドキュメントの場所。
     *
     * <p><strong>相対パスを決め打ちにしない。</strong> テストの作業ディレクトリは
     * 実行のしかた（Gradle / IDE）で変わる。リポジトリの根を探して組み立てる。
     */
    private static Path 設計ドキュメント() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("docs/design/data-model.md");
            if (Files.exists(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("docs/design/data-model.md が見つかりません");
    }

    private Set<String> 実スキーマの列(String table) {
        return new LinkedHashSet<>(jdbcTemplate.queryForList(
                """
                SELECT LOWER(column_name) FROM information_schema.columns
                 WHERE LOWER(table_name) = ?
                """,
                String.class, table.toLowerCase(Locale.ROOT)));
    }
}
