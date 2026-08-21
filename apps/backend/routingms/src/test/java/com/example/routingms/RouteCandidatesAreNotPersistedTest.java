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
