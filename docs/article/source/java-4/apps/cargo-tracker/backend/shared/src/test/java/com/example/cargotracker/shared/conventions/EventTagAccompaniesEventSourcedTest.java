package com.example.cargotracker.shared.conventions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
 * {@code @EventSourced(tagKey)} の集約が出すイベントは {@code @EventTag} を持つ
 * （[ADR-0001] 決定 5 第 8 項）。
 *
 * <p><b>この検査が無かったために、同じ欠陥を 2 度作りました。</b> IT2 で `Cargo` に
 * {@code @EventTag} が要ると分かって ADR に書いたのに、`Shipper` 側は残りました。
 * 「付け忘れると集約が空のまま復元される」と本文に書くだけでは、次の集約で同じ
 * ことが起きます。</p>
 *
 * <p><b>集約の単体テストでは判別できません。</b> {@code AxonTestFixture} の
 * {@code disableAxonServer()} ではタグ復元が働かず、守りを外しても緑になります
 * （決定 5 第 11 項）。だから静的に見ます。</p>
 *
 * <p><b>「タグを持つイベント」だけを探しません。</b> 集約が出しうるイベントを
 * 全部拾ってから、タグの有無を見ます。正しい形の行だけを拾う検査は、書いていない
 * ものを素通りさせます。</p>
 */
class EventTagAccompaniesEventSourcedTest {

    private static final Pattern TAG_KEY =
            Pattern.compile("@EventSourced\\s*\\([^)]*tagKey\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern APPENDED_EVENT =
            Pattern.compile("appender\\.append\\(\\s*new\\s+(\\w+)\\s*\\(");

    private static Path backendRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("settings.gradle.kts"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("settings.gradle.kts が見つかりません");
    }

    private static List<Path> mainSources() throws IOException {
        try (Stream<Path> paths = Files.walk(backendRoot())) {
            return paths
                    .filter(p -> p.toString().replace('\\', '/').contains("/src/main/java/"))
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .toList();
        }
    }

    /** 集約とそのタグキー、その集約が {@code appender.append} で出すイベントの型名。 */
    private record Aggregate(Path file, String tagKey, List<String> events) {
    }

    private static List<Aggregate> eventSourcedAggregates() throws IOException {
        List<Aggregate> found = new ArrayList<>();
        for (Path file : mainSources()) {
            String body = Files.readString(file, StandardCharsets.UTF_8);
            Matcher tag = TAG_KEY.matcher(body);
            if (!tag.find()) {
                continue;
            }
            List<String> events = new ArrayList<>();
            Matcher appended = APPENDED_EVENT.matcher(body);
            while (appended.find()) {
                events.add(appended.group(1));
            }
            found.add(new Aggregate(file, tag.group(1), events));
        }
        return found;
    }

    private static Path sourceOf(String simpleName) throws IOException {
        return mainSources().stream()
                .filter(p -> p.getFileName().toString().equals(simpleName + ".java"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(simpleName + " の定義が見つかりません"));
    }

    @Test
    @DisplayName("ADR-0001 決定 5 第 8 項: 集約が出すイベントはタグを宣言する")
    void appendedEventsDeclareTheTag() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (Aggregate aggregate : eventSourcedAggregates()) {
            for (String eventName : aggregate.events()) {
                String eventBody = Files.readString(sourceOf(eventName), StandardCharsets.UTF_8);
                boolean declaresTag =
                        eventBody.contains("@EventTag(key = \"" + aggregate.tagKey() + "\")");
                if (!declaresTag) {
                    offenders.add(eventName + "（" + aggregate.file().getFileName()
                            + " の tagKey=" + aggregate.tagKey() + "）");
                }
            }
        }

        assertThat(offenders)
                .as("@EventTag(key) が無いイベントは、集約を空のまま復元させる。"
                        + "復元した状態を見る守りが丸ごと素通りし、集約の単体テストでは判別できない")
                .isEmpty();
    }

    @Test
    @DisplayName("検査の対象が実在する（空振りしていない）")
    void actuallyInspectsAggregates() throws IOException {
        List<Aggregate> aggregates = eventSourcedAggregates();

        assertThat(aggregates)
                .as("@EventSourced(tagKey) の集約が 1 つも見つからないなら、"
                        + "上の検査は『守っている』ではなく『調べていない』")
                .isNotEmpty();
        assertThat(aggregates.stream().flatMap(a -> a.events().stream()).toList())
                .as("イベントを 1 つも拾えていないなら、appender.append の書き方が変わっている")
                .isNotEmpty();
    }
}
