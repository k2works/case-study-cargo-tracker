package com.example.cargotracker.support;

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
 * <strong>SQL の正しさを検証する場所を実 PostgreSQL に固定する</strong>（ADR-003）。
 *
 * <p>ADR-003 は「Repository / MyBatis Mapper のテストは Testcontainers（PostgreSQL 16）」
 * と定め、これを<strong>「SQL の正しさを検証する唯一の場所」</strong>と呼んでいる。
 * H2 で検証すると、方言差（{@code TIMESTAMPTZ}・部分インデックス・{@code NUMERIC} の丸め）が
 * <strong>本番障害として現れる</strong>。
 *
 * <p><strong>この規則に守り手が無かった。</strong> IT16 で ADR に守り手を書き出したときに
 * 判明した。現時点で {@code *RepositoryTest} は 9 件すべてが
 * {@code PostgreSQLIntegrationTestBase} を継承しているが、
 * <strong>10 件目が H2 で書かれても何も落ちなかった</strong>。
 *
 * <p>ADR-012 の第 3 項と同じ形である — <strong>いま守れていることと、
 * 明日も守れることは別である。</strong>0 件・全件一致であることを検査で守る。
 *
 * <p>方言差そのものの検査は {@link H2DialectSmokeTest} が逆方向から受け持つ
 * （PostgreSQL でしか解釈できない SQL を書くと、ローカル起動だけが落ちる）。
 * <strong>本検査は「どこで検証するか」を、あちらは「両方で動くか」を見る。</strong>
 */
@DisplayName("SQL を検証するテストは実 PostgreSQL の上で書く（ADR-003）")
class PostgreSQLTestBaseTest {

    private static final Path TEST_ROOT = Path.of("src/test/java");
    private static final String BASE = "PostgreSQLIntegrationTestBase";

    /**
     * <strong>{@code *RepositoryTest} は実 PostgreSQL の基底を継承する。</strong>
     *
     * <p>違反があればクラス名を並べて落とす。
     */
    @Test
    void リポジトリのテストは実PostgreSQLの基底を継承する() throws IOException {
        List<Path> repositoryTests = testFilesMatching("RepositoryTest.java");
        assertThat(repositoryTests)
                .as("リポジトリのテストが 1 つも見つからないなら、検査は何も見ていない")
                .isNotEmpty();

        List<String> violations = new ArrayList<>();
        for (Path source : repositoryTests) {
            if (!extendsPostgreSqlBase(Files.readString(source))) {
                violations.add(source.getFileName().toString());
            }
        }

        assertThat(violations)
                .as("""
                        SQL を検証するテストが実 PostgreSQL の上に無い（ADR-003）。

                        H2 で SQL の正しさを判断すると、方言差
                        （TIMESTAMPTZ・部分インデックス・NUMERIC の丸め）が
                        **本番障害として現れます。**
                        PostgreSQLIntegrationTestBase を継承してください。""")
                .isEmpty();
    }

    /**
     * <strong>検査そのものが働くことを確かめる</strong>（メタテスト）。
     *
     * <p><strong>フィクスチャは実コードの形で作る。</strong> 「最小の違反例」だけだと、
     * メタテストが緑でも実コードの違反を見逃す（ADR-015 で学んだ形）。
     */
    @Test
    void 実コードの形の違反と正しい形を見分けられる() {
        String violatingShape = """
                @MybatisTest
                @ActiveProfiles("h2-dialect")
                @DisplayName("キャンセル申請の永続化")
                class CancellationRequestRepositoryTest {

                    @Autowired
                    private CancellationMapper mapper;
                }
                """;

        String validShape = """
                /** キャンセル申請の永続化（US30）。 */
                @DisplayName("キャンセル申請の永続化")
                class CancellationRequestRepositoryTest extends PostgreSQLIntegrationTestBase {

                    @Autowired
                    private CancellationMapper mapper;
                }
                """;

        assertThat(extendsPostgreSqlBase(violatingShape))
                .as("H2 プロファイルで書かれたリポジトリテストを違反として拾えること")
                .isFalse();
        assertThat(extendsPostgreSqlBase(validShape))
                .as("正しい形を違反にしないこと（常に落ちる検査で緑にしない）")
                .isTrue();
    }

    /**
     * <strong>基底クラスそのものが実 PostgreSQL を使っている。</strong>
     *
     * <p>継承だけを見ると、<strong>基底の中身を H2 に差し替えたときに全件が素通りする</strong>。
     * 名簿方式の検査が名簿に無いものを通したのと同じ形の穴であり、
     * <strong>検査の足元を検査する。</strong>
     */
    @Test
    void 基底クラスがTestcontainersのPostgreSQLを使う() throws IOException {
        Path base = testFilesMatching(BASE + ".java").stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError(BASE + " が見つかりません"));

        String source = Files.readString(base);

        assertThat(source)
                .as("基底が Testcontainers の PostgreSQL を起動していること（ADR-003）")
                .contains("PostgreSQLContainer");
        assertThat(source)
                .as("基底が H2 に差し替えられていないこと")
                .doesNotContain("h2-dialect");
    }

    private static boolean extendsPostgreSqlBase(String source) {
        return source.contains("extends " + BASE);
    }

    private static List<Path> testFilesMatching(String suffix) throws IOException {
        try (Stream<Path> paths = Files.walk(TEST_ROOT)) {
            return paths
                    .filter(p -> p.getFileName().toString().endsWith(suffix))
                    .sorted()
                    .toList();
        }
    }
}
