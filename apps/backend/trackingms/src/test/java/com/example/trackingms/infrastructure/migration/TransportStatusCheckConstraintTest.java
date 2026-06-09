package com.example.trackingms.infrastructure.migration;

import com.example.trackingms.domain.model.TransportStatus;
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
 * TransportStatus enum 値が、Flyway migration の最新版 CHECK 制約
 * （tracking_summary / tracking_event の 2 箇所）に「全て」含まれることを検証する。
 *
 * <p>IT9 V5 バグの再発防止策を trackingms に横展開したもの。enum 側に値を追加
 * して migration を忘れた場合、本テストが赤くなり PR レベルで検知される。</p>
 *
 * <p>解析戦略は billingms / handlingms 側と同じ：{@code src/main/resources/db/migration}
 * 配下の {@code V*.sql} をバージョン順に処理し、各 CHECK 制約の最新版を取得する。
 * tracking_event 側は NULL 許容のため {@code IS NULL OR ... IN (...)} 形式を解釈する。</p>
 */
class TransportStatusCheckConstraintTest {

    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");

    private static final Pattern SUMMARY_PATTERN = Pattern.compile(
            "ADD\\s+CONSTRAINT\\s+chk_tracking_summary_current_status\\s+CHECK\\s*\\(\\s*"
                    + "current_status\\s+IN\\s*\\(([^)]+)\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern EVENT_PATTERN = Pattern.compile(
            "ADD\\s+CONSTRAINT\\s+chk_tracking_event_transport_status\\s+CHECK\\s*\\(\\s*"
                    + "transport_status\\s+IS\\s+NULL\\s+OR\\s+transport_status\\s+IN\\s*\\(([^)]+)\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Test
    @DisplayName("TransportStatus enum の全値が tracking_summary.current_status の CHECK 制約に含まれる")
    void allEnumValuesAreInSummaryCheckConstraint() throws Exception {
        Set<String> allowed = extractLatest(SUMMARY_PATTERN);
        Set<String> enumValues = enumNames();
        assertThat(allowed)
                .as("enum %s 全値が summary CHECK に含まれること", enumValues)
                .containsAll(enumValues);
    }

    @Test
    @DisplayName("TransportStatus enum の全値が tracking_event.transport_status の CHECK 制約に含まれる")
    void allEnumValuesAreInEventCheckConstraint() throws Exception {
        Set<String> allowed = extractLatest(EVENT_PATTERN);
        Set<String> enumValues = enumNames();
        assertThat(allowed)
                .as("enum %s 全値が event CHECK に含まれること", enumValues)
                .containsAll(enumValues);
    }

    @Test
    @DisplayName("複数 ADD CONSTRAINT が同一 SQL に現れた場合、最後の定義が採用される（リファクタリングで順序を変えてもロバスト）")
    void multipleAddConstraintsRobustToOrdering() {
        String sql = """
                ALTER TABLE tracking_summary ADD CONSTRAINT chk_tracking_summary_current_status CHECK (
                    current_status IN ('NOT_RECEIVED', 'RECEIVED', 'LOADED')
                );
                -- 後続 migration で値域を拡張
                ALTER TABLE tracking_summary DROP CONSTRAINT IF EXISTS chk_tracking_summary_current_status;
                ALTER TABLE tracking_summary ADD CONSTRAINT chk_tracking_summary_current_status CHECK (
                    current_status IN ('NOT_RECEIVED', 'RECEIVED', 'LOADED', 'IN_TRANSIT',
                                       'UNLOADED', 'AWAITING_CLAIM', 'DELIVERED',
                                       'MISROUTED', 'EXCEPTION')
                );
                """;

        Matcher m = SUMMARY_PATTERN.matcher(sql);
        Set<String> latest = null;
        while (m.find()) {
            latest = parseInClause(m.group(1));
        }

        assertThat(latest)
                .as("最新の ADD CONSTRAINT 値が採用される（DROP の位置 / 数に依存しない）")
                .containsExactly("NOT_RECEIVED", "RECEIVED", "LOADED", "IN_TRANSIT",
                        "UNLOADED", "AWAITING_CLAIM", "DELIVERED", "MISROUTED", "EXCEPTION");
    }

    @Test
    @DisplayName("各 CHECK 制約に enum に存在しない値が混入していない")
    void noOrphanValuesInCheckConstraints() throws Exception {
        Set<String> summaryAllowed = extractLatest(SUMMARY_PATTERN);
        Set<String> eventAllowed = extractLatest(EVENT_PATTERN);
        Set<String> enumValues = enumNames();
        assertThat(enumValues).as("summary CHECK 値 %s が enum に存在", summaryAllowed)
                .containsAll(summaryAllowed);
        assertThat(enumValues).as("event CHECK 値 %s が enum に存在", eventAllowed)
                .containsAll(eventAllowed);
    }

    private Set<String> enumNames() {
        return Arrays.stream(TransportStatus.values())
                .map(Enum::name)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> extractLatest(Pattern pattern) throws Exception {
        Set<String> latest = null;
        try (var stream = Files.list(MIGRATION_DIR)) {
            var files = stream
                    .filter(p -> p.getFileName().toString().matches("V\\d+__.*\\.sql"))
                    .sorted()
                    .toList();
            for (Path file : files) {
                String sql = Files.readString(file);
                Matcher m = pattern.matcher(sql);
                while (m.find()) {
                    latest = parseInClause(m.group(1));
                }
            }
        }
        if (latest == null) {
            throw new AssertionError("対象 CHECK 制約が migration から見つからない: " + pattern);
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
