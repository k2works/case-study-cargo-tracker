package com.example.cargotracker.shared.conventions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Axon Server コンテナの組み立てが 1 か所にあること。
 *
 * <p>IT2 で 3 か所に散っていた。起動猶予を延ばしたとき 1 つ直し漏れ、<b>その 1 つは
 * 単独実行では通り、全体ビルドでだけ落ちた</b>。同じ組み立てを各テストで書くと、
 * 設定を片方だけ直すことになる。</p>
 *
 * <p><b>正しい形だけを拾わない。</b> {@code new AxonServerContainer} を含む行を
 * すべて数え、許した 1 か所以外があれば赤にする。「正しい書き方をしている行」だけを
 * 探す検査は、書いていない違反を素通りさせる。</p>
 */
class AxonServerContainerIsBuiltInOnePlaceTest {

    private static final String BUILDER =
            "shared/src/testFixtures/java/com/example/cargotracker/shared/testing/"
                    + "AbstractAxonIntegrationTest.java";

    /** この検査自身。探している字面を本文に持つので、対象から外す。 */
    private static final String SELF =
            "shared/src/test/java/com/example/cargotracker/shared/conventions/"
                    + "AxonServerContainerIsBuiltInOnePlaceTest.java";

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

    @Test
    @DisplayName("コンテナの組み立ては AbstractAxonIntegrationTest だけが持つ")
    void hasSingleBuilder() throws IOException {
        Path root = backendRoot();
        List<String> offenders;
        try (Stream<Path> files = Files.walk(root)) {
            offenders = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("/build/"))
                    .filter(p -> containsConstruction(p))
                    .map(p -> root.relativize(p).toString())
                    .filter(p -> !p.equals(BUILDER) && !p.equals(SELF))
                    .sorted()
                    .toList();
        }

        assertThat(offenders)
                .as("組み立てが散ると、起動猶予のような設定を片方だけ直すことになる。"
                        + "AbstractAxonIntegrationTest.axonServerContainer() を使う")
                .isEmpty();
    }

    @Test
    @DisplayName("組み立てそのものは実在する（検査が空振りしていない）")
    void builderExists() {
        // 対象が 0 件なら、上の検査は「守っている」ではなく「何も見ていない」。
        assertThat(containsConstruction(backendRoot().resolve(BUILDER)))
                .as("組み立てが見つからないなら、名前を変えたときに上の検査が空振りする")
                .isTrue();
    }

    private static boolean containsConstruction(Path file) {
        try {
            return Files.readString(file).contains("new AxonServerContainer");
        } catch (IOException e) {
            throw new IllegalStateException("読めませんでした: " + file, e);
        }
    }
}
