package com.example.routingms;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-017 の決定 2 のコンプライアンス。<strong>経路候補を永続化しない。</strong>
 *
 * <p>候補は航海スケジュールの写像であり、スケジュールが変われば古くなる。保存すると
 * 「保存した時点では正しかった候補」を運用中に持ち続けることになり、どちらが正か
 * 分からなくなる。保存が要るのは<strong>選んだあと</strong>で、それは US09 で
 * `Cargo` の旅程として bookingms に保存する。
 *
 * <p>否定の決定は「やらないこと」なので、書き忘れではなく<strong>足したこと</strong>を
 * 検出する形にする。routing_db が持ってよい表を列挙し、それ以外の表を作ったら落とす。
 */
@DisplayName("経路候補を永続化しない（ADR-017）")
class RouteCandidatesAreNotPersistedTest {

    /**
     * 港湾制約を持たない（[ADR-018] の決定 3）。
     *
     * <p>ADR-018 は「否定の決定であり検査に落とせない」と書いていたが、**落とせる**。
     * 隣で経路候補の非永続化を許可リストで検査しているのと同じ形で、
     * <strong>足したこと</strong>を検出すればよい。文章は読まれなければ効かないが、
     * テストは読まれなくても効く。
     */
    @Test
    @DisplayName("港湾制約のモデルを持たない（ADR-018 決定 3）")
    void doesNotModelPortConstraints() throws IOException {
        // 表として持たない。1 つも読めていなければ、この検査は何も守らない
        assertThat(createdTables())
                .as("港湾制約の表がある。対応できる貨物種別は港ではなく航海が持つ（ADR-018 決定 3）。"
                        + "持つと決め直すなら、まず ADR-018 を書き換えること")
                .isNotEmpty()
                .noneMatch(table -> table.contains("port_constraint") || table.contains("port_capab"));

        // 型としても持たない
        assertThat(domainTypeNames())
                .as("港湾制約を表す型がある。ADR-018 決定 3 を読み直すこと")
                .noneMatch(name -> name.startsWith("PortConstraint") || name.startsWith("PortCapability"));
    }

    private List<String> domainTypeNames() throws IOException {
        Path dir = Path.of("src/main/java/com/example/routingms/domain/model").toAbsolutePath();
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".java"))
                    .map(name -> name.substring(0, name.length() - ".java".length()))
                    .toList();
        }
    }

    /**
     * routing_db が持ってよい表。
     *
     * <p>ここを増やすときは ADR-017 を読み直すこと。経路候補・旅程・探索結果の
     * キャッシュを足すのは、この決定を変えることを意味する。
     */
    private static final List<String> ALLOWED_TABLES = List.of(
            "location", "voyage", "carrier_movement",
            // Flyway 自身の管理表
            "flyway_schema_history", "schema_bootstrap");

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([a-z_][a-z0-9_]*)",
            Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("routing_db に経路候補を保存する表を作っていない")
    void doesNotCreateTablesForCandidates() throws IOException {
        List<String> tables = createdTables();

        assertThat(tables)
                .as("マイグレーションが 1 つも読めていない場合、この検査は何も守らない。"
                        + "許していない表があれば、経路候補を保存しようとしている（ADR-017 決定 2）。"
                        + "保存が要るのは選んだあとで、それは US09 で bookingms が持つ")
                .isNotEmpty()
                .isSubsetOf(ALLOWED_TABLES);
    }

    private List<String> createdTables() throws IOException {
        Path dir = Path.of("src/main/resources/db/migration").toAbsolutePath();
        try (Stream<Path> files = Files.list(dir)) {
            StringBuilder all = new StringBuilder();
            for (Path file : files.filter(p -> p.toString().endsWith(".sql")).sorted().toList()) {
                all.append(Files.readString(file)).append('\n');
            }
            Matcher matcher = CREATE_TABLE.matcher(all.toString());
            List<String> tables = new java.util.ArrayList<>();
            while (matcher.find()) {
                tables.add(matcher.group(1).toLowerCase(Locale.ROOT));
            }
            return tables;
        }
    }
}
