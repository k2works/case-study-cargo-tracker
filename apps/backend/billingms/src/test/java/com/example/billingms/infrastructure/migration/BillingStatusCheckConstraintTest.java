package com.example.billingms.infrastructure.migration;

import com.example.billingms.domain.model.BillingStatus;
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
 * IT9 V5 バグ（chk_invoice_status に PARTIALLY_PAID が含まれていなかった結果、
 * 実行時に CHECK 制約違反で部分入金記録が失敗した）の再発を防ぐ。
 *
 * <p>BillingStatus enum 値が、Flyway migration の最新版 chk_invoice_status
 * CHECK 制約に列挙された値の集合に「全て」含まれることを検証する。enum 側に値を
 * 追加して migration を忘れた場合、本テストが赤くなり CI で検知できる。</p>
 *
 * <p>migration ファイルは {@code src/main/resources/db/migration} 配下の V*.sql を
 * バージョン順に処理し、{@code DROP CONSTRAINT IF EXISTS chk_invoice_status} の後に
 * {@code ADD CONSTRAINT chk_invoice_status CHECK (billing_status IN (...))} を見つけた
 * 場合は最新版で上書きする（実際の DB 適用順と同じ振る舞い）。</p>
 */
class BillingStatusCheckConstraintTest {

    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");
    private static final Pattern CHECK_PATTERN = Pattern.compile(
            "ADD\\s+CONSTRAINT\\s+chk_invoice_status\\s+CHECK\\s*\\(\\s*"
                    + "billing_status\\s+IN\\s*\\(([^)]+)\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Test
    @DisplayName("BillingStatus enum の全値が chk_invoice_status CHECK 制約に含まれる")
    void allEnumValuesAreInCheckConstraint() throws Exception {
        Set<String> allowed = extractLatestAllowedStatuses();

        Set<String> enumValues = Arrays.stream(BillingStatus.values())
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
                ALTER TABLE invoice ADD CONSTRAINT chk_invoice_status CHECK (
                    billing_status IN ('PENDING', 'CALCULATED', 'INVOICED', 'PAID', 'OVERDUE', 'CANCELLED')
                );
                -- 後続 migration で再定義（IT9 V5 相当の rewrite シナリオ）
                ALTER TABLE invoice DROP CONSTRAINT IF EXISTS chk_invoice_status;
                ALTER TABLE invoice ADD CONSTRAINT chk_invoice_status CHECK (
                    billing_status IN ('PENDING', 'CALCULATED', 'INVOICED', 'PARTIALLY_PAID', 'PAID', 'OVERDUE', 'CANCELLED')
                );
                """;

        Matcher m = CHECK_PATTERN.matcher(sql);
        Set<String> latest = null;
        while (m.find()) {
            latest = parseInClause(m.group(1));
        }

        assertThat(latest)
                .as("最新の ADD CONSTRAINT 値が採用される（DROP の位置 / 数に依存しない）")
                .containsExactly("PENDING", "CALCULATED", "INVOICED", "PARTIALLY_PAID",
                        "PAID", "OVERDUE", "CANCELLED");
    }

    @Test
    @DisplayName("chk_invoice_status CHECK 制約に enum に存在しない値が混入していない")
    void noOrphanValuesInCheckConstraint() throws Exception {
        Set<String> allowed = extractLatestAllowedStatuses();

        Set<String> enumValues = Arrays.stream(BillingStatus.values())
                .map(Enum::name)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        assertThat(enumValues)
                .as("CHECK 制約値 %s が enum に存在すること（孤児値なし）", allowed)
                .containsAll(allowed);
    }

    private Set<String> extractLatestAllowedStatuses() throws Exception {
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
                    String values = m.group(1);
                    latest = parseInClause(values);
                }
            }
        }
        if (latest == null) {
            throw new AssertionError("chk_invoice_status CHECK 制約が migration から見つからない");
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
