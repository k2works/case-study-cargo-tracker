package com.example.handlingms.infrastructure.migration;

import com.example.handlingms.domain.model.HandlingType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HandlingType enum 値が、Flyway migration の最新版 chk_handling_type
 * CHECK 制約に列挙された値の集合に「全て」含まれることを検証する。
 *
 * <p>IT9 V5 バグ（billingms の chk_invoice_status 制約値リスト ⊂ enum 不整合）の
 * 再発防止策を handlingms に横展開したもの。enum 側に値を追加して migration を
 * 忘れた場合、本テストが赤くなり PR レベルで検知される。</p>
 *
 * <p>migration 解析戦略は billingms 側の {@code BillingStatusCheckConstraintTest}
 * と同じ：{@code src/main/resources/db/migration} 配下の {@code V*.sql} を
 * バージョン順に処理し、{@code ADD CONSTRAINT chk_handling_type CHECK (...)} を
 * 見つけたら最新版で上書きする（DB 適用順と同じ振る舞い）。</p>
 */
class HandlingTypeCheckConstraintTest {

    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");
    private static final Pattern CHECK_PATTERN = Pattern.compile(
            "ADD\\s+CONSTRAINT\\s+chk_handling_type\\s+CHECK\\s*\\(\\s*"
                    + "handling_type\\s+IN\\s*\\(([^)]+)\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Test
    @DisplayName("HandlingType enum の全値が chk_handling_type CHECK 制約に含まれる")
    void allEnumValuesAreInCheckConstraint() throws Exception {
        Set<String> allowed = extractLatestAllowedValues();

        Set<String> enumValues = Arrays.stream(HandlingType.values())
                .map(Enum::name)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        assertThat(allowed)
                .as("enum %s 全値が CHECK 制約に含まれること", enumValues)
                .containsAll(enumValues);
    }

    @Test
    @DisplayName("複数 ADD CONSTRAINT が同一 SQL に現れた場合、最後の定義が採用される（リファクタリングで順序を変えてもロバスト）")
    void multipleAddConstraintsRobustToOrdering() {
        String sql = """
                ALTER TABLE handling_activity ADD CONSTRAINT chk_handling_type CHECK (
                    handling_type IN ('RECEIVE', 'LOAD', 'UNLOAD')
                );
                -- 後続 migration で値域を拡張
                ALTER TABLE handling_activity DROP CONSTRAINT IF EXISTS chk_handling_type;
                ALTER TABLE handling_activity ADD CONSTRAINT chk_handling_type CHECK (
                    handling_type IN ('RECEIVE', 'LOAD', 'UNLOAD', 'CLAIM', 'CUSTOMS')
                );
                """;

        Matcher m = CHECK_PATTERN.matcher(sql);
        Set<String> latest = null;
        while (m.find()) {
            latest = parseInClause(m.group(1));
        }

        assertThat(latest)
                .as("最新の ADD CONSTRAINT 値が採用される（DROP の位置 / 数に依存しない）")
                .containsExactly("RECEIVE", "LOAD", "UNLOAD", "CLAIM", "CUSTOMS");
    }

    @Test
    @DisplayName("chk_handling_type CHECK 制約に enum に存在しない値が混入していない")
    void noOrphanValuesInCheckConstraint() throws Exception {
        Set<String> allowed = extractLatestAllowedValues();

        Set<String> enumValues = Arrays.stream(HandlingType.values())
                .map(Enum::name)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        assertThat(enumValues)
                .as("CHECK 制約値 %s が enum に存在すること（孤児値なし）", allowed)
                .containsAll(allowed);
    }

    private Set<String> extractLatestAllowedValues() throws Exception {
        Set<String> latest = null;
        try (var stream = Files.list(MIGRATION_DIR)) {
            var files = stream
                    .filter(p -> p.getFileName().toString().matches("V\\d+__.*\\.sql"))
                    .sorted()
                    .toList();
            for (Path file : files) {
                String sql = Files.readString(file);
                Matcher m = CHECK_PATTERN.matcher(sql);
                while (m.find()) {
                    latest = parseInClause(m.group(1));
                }
            }
        }
        if (latest == null) {
            throw new AssertionError("chk_handling_type CHECK 制約が migration から見つからない");
        }
        return latest;
    }

    private Set<String> parseInClause(String values) {
        Set<String> result = new LinkedHashSet<>();
        for (String token : values.split(",")) {
            String cleaned = token.trim().replaceAll("^'|'$", "").trim();
            if (!cleaned.isEmpty()) {
                result.add(cleaned);
            }
        }
        return result;
    }
}
