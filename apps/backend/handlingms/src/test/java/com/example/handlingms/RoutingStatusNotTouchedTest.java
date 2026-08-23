package com.example.handlingms;

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
 * [ADR-023] 決定 3 のコンプライアンス。<strong>IT7 は経路の状態を動かさない</strong>。
 *
 * <p>予定と違う場所の作業は「記録に残す」までで、{@code RoutingStatus} を
 * {@code MISROUTED} へ動かすのは US28（IT10）である。
 *
 * <p><strong>否定の決定も検査に落とす。</strong>落とさなければ、あとから動かす実装が
 * 入ったとき「決定を意図的に覆したのか、写し漏れたのか」が区別できない。
 *
 * <p>この検査が落ちたら、それは<strong>US28 に着手した合図</strong>である。
 * ADR-023 決定 3 を更新してからこの検査を外すこと。
 */
@DisplayName("経路の状態を動かさない（ADR-023 決定 3）")
class RoutingStatusNotTouchedTest {

    private static final Path MAIN =
            Path.of("src/main/java").toAbsolutePath().normalize();

    /**
     * 経路の状態に関わる語。
     *
     * <p>型を持っていないので ArchUnit では見られない。<strong>語が現れないこと</strong>で
     * 見る——粗いが、動かす実装を書けば必ずどれかが現れる。
     */
    private static final List<String> ROUTING_STATUS_WORDS =
            List.of("MISROUTED", "RoutingStatus", "routingStatus");

    @Test
    @DisplayName("handlingms は経路の状態を扱わない")
    void doesNotTouchRoutingStatus() throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                for (String word : ROUTING_STATUS_WORDS) {
                    if (source.contains(word)) {
                        found.add("%s に %s".formatted(file.getFileName(), word));
                    }
                }
            }
        }

        assertThat(found)
                .as("経路の状態を扱う実装が入っている。MISROUTED へ動かすのは US28（IT10）で、"
                        + "ADR-023 決定 3 はそれまで動かさないと決めた")
                .isEmpty();
    }

    /** 読み取れていないと、この検査は何も守らない。 */
    @Test
    @DisplayName("本番のソースを実際に読んでいる")
    void actuallyReadsTheSources() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN)) {
            assertThat(files.filter(p -> p.toString().endsWith(".java")).count())
                    .as("ソースが 1 件も読めていない")
                    .isGreaterThan(5);
        }
    }
}
