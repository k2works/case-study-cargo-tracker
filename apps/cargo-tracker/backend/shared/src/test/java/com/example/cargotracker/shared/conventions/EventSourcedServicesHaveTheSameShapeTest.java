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
 * Event Sourcing のサービスは同じ形で立ち上げる。
 *
 * <p>bookingms で得た知見（IT2）を routingms で入れ直したのは、形が同じであることを
 * どこにも書いていなかったからです。<b>3 つ目のサービス（trackingms / IT8）で同じ失敗を
 * 繰り返さないために、形そのものを検査に落とします。</b></p>
 *
 * <p>{@code @EventTag} は {@link EventTagAccompaniesEventSourcedTest} が見ています。
 * ここで見るのは残りの 3 つです。</p>
 *
 * <ol>
 *   <li>コマンドハンドラが static でない（static が勝つと 2 度目の受付が素通りする）</li>
 *   <li>投影のパッケージが {@code application.yml} の Processing Group に列挙されている
 *       （列挙し忘れると既定の設定で動くので、テストは緑のまま本番だけ挙動が変わる）</li>
 *   <li>投影を持つサービスに {@code ReplayIT} がある（リプレイで副作用が積み上がらない
 *       ことは、静的な依存では確かめられない）</li>
 * </ol>
 *
 * <p><b>正しい形のものだけを探しません。</b> {@code @EventSourced} が付いた集約を全部
 * 拾ってから、それぞれについて 3 つを見ます。載っているものだけを数える検査は、
 * 載せ忘れたものほど漏らします。</p>
 */
class EventSourcedServicesHaveTheSameShapeTest {

    // 行頭のものだけを拾う。Javadoc で {@code @EventSourced(tagKey)} に触れている
    // イベントやユーティリティを集約と取り違えない。
    private static final Pattern EVENT_SOURCED =
            Pattern.compile("(?m)^@EventSourced\\s*\\(");
    private static final Pattern COMMAND_HANDLER_SIGNATURE = Pattern.compile(
            "@CommandHandler\\s*(?://[^\\n]*\\n\\s*)*(public|protected|private)?\\s*"
                    + "(static\\s+)?");

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

    /** Event Sourcing の集約と、それが属するサービスのディレクトリ。 */
    private record Aggregate(Path file, Path serviceDir, String servicePackage) {
    }

    private static List<Aggregate> eventSourcedAggregates() throws IOException {
        List<Aggregate> found = new ArrayList<>();
        for (Path file : mainSources()) {
            String body = Files.readString(file, StandardCharsets.UTF_8);
            if (!EVENT_SOURCED.matcher(body).find()) {
                continue;
            }
            String path = file.toString().replace('\\', '/');
            int at = path.indexOf("/src/main/java/");
            Path serviceDir = Path.of(path.substring(0, at));
            Matcher pkg = Pattern.compile("package\\s+([\\w.]+)\\.domain\\.model")
                    .matcher(body);
            found.add(new Aggregate(file, serviceDir, pkg.find() ? pkg.group(1) : null));
        }
        return found;
    }

    @Test
    @DisplayName("Event Sourcing の集約が 2 つ以上ある（検査が空振りしていない）")
    void thereAreAggregatesToCheck() throws IOException {
        // 対象が 0 件でも他の検査は緑になる。まず対象があることを見る。
        assertThat(eventSourcedAggregates()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("コマンドハンドラは static でない")
    void commandHandlersAreInstanceMethods() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Aggregate aggregate : eventSourcedAggregates()) {
            String body = Files.readString(aggregate.file(), StandardCharsets.UTF_8);
            Matcher matcher = COMMAND_HANDLER_SIGNATURE.matcher(body);
            while (matcher.find()) {
                if (matcher.group(2) != null) {
                    offenders.add(aggregate.file().getFileName().toString());
                }
            }
        }
        assertThat(offenders)
                .as("static の作成ハンドラを置くと、集約が既に存在しても static が呼ばれ、"
                        + "2 度目の受付が通る（IT2 で実測）")
                .isEmpty();
    }

    @Test
    @DisplayName("投影のパッケージが Processing Group として application.yml に列挙されている")
    void projectionPackagesAreEnumerated() throws IOException {
        List<String> missing = new ArrayList<>();
        for (Aggregate aggregate : eventSourcedAggregates()) {
            Path projectionDir = aggregate.serviceDir().resolve("src/main/java")
                    .resolve(aggregate.servicePackage().replace('.', '/'))
                    .resolve("infrastructure/projection");
            if (!hasJavaClass(projectionDir)) {
                continue; // 投影を持たないサービスには列挙するものが無い。
            }
            Path yml = aggregate.serviceDir().resolve("src/main/resources/application.yml");
            String config = Files.exists(yml)
                    ? Files.readString(yml, StandardCharsets.UTF_8) : "";
            String expected = aggregate.servicePackage() + ".infrastructure.projection";
            if (!config.contains(expected)) {
                missing.add(expected);
            }
        }
        assertThat(missing)
                .as("列挙し忘れると既定の設定で動くので、テストは緑のまま本番だけ挙動が変わる")
                .isEmpty();
    }

    @Test
    @DisplayName("投影を持つサービスには ReplayIT がある")
    void projectionsHaveAReplayCheck() throws IOException {
        List<String> missing = new ArrayList<>();
        for (Aggregate aggregate : eventSourcedAggregates()) {
            Path projectionDir = aggregate.serviceDir().resolve("src/main/java")
                    .resolve(aggregate.servicePackage().replace('.', '/'))
                    .resolve("infrastructure/projection");
            if (!hasJavaClass(projectionDir)) {
                continue;
            }
            Path testDir = aggregate.serviceDir().resolve("src/test/java");
            if (!containsFileNamed(testDir, "ReplayIT.java")) {
                missing.add(aggregate.serviceDir().getFileName().toString());
            }
        }
        assertThat(missing)
                .as("リプレイで副作用が積み上がらないことは、静的な依存では確かめられない")
                .isEmpty();
    }

    private static boolean hasJavaClass(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> paths = Files.list(dir)) {
            return paths.anyMatch(p -> p.getFileName().toString().endsWith(".java")
                    && !p.getFileName().toString().equals("package-info.java"));
        }
    }

    private static boolean containsFileNamed(Path dir, String name) throws IOException {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            return paths.anyMatch(p -> p.getFileName().toString().equals(name));
        }
    }
}
