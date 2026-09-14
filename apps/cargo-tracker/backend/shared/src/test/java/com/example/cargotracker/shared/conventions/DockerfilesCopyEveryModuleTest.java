package com.example.cargotracker.shared.conventions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Dockerfile は依存の解決に<b>全モジュールの build.gradle.kts を要る</b>。
 *
 * <p>Gradle の設定は {@code settings.gradle.kts} が全モジュールを include するので、
 * 1 つでも写し忘れると「存在しないディレクトリを構成できません」で
 * <b>すべてのイメージが作れなくなる</b>——実際に simulationms を足したとき、
 * 7 つの Dockerfile が同時に壊れた（IT16）。</p>
 *
 * <p><b>名簿で緩めず数え上げる。</b> 「足したら 8 か所を直す」という約束は、
 * 8 か所あるという事実そのものが忘れられる。include の一覧を出典にして、
 * Dockerfile の側を照合する。</p>
 */
class DockerfilesCopyEveryModuleTest {

    private static final Path BACKEND = Path.of("..").toAbsolutePath().normalize();

    private static final Pattern INCLUDE = Pattern.compile("^include\\(\"([^\"]+)\"\\)",
            Pattern.MULTILINE);

    private static List<String> modules() throws IOException {
        String settings = Files.readString(BACKEND.resolve("settings.gradle.kts"),
                StandardCharsets.UTF_8);
        Matcher matcher = INCLUDE.matcher(settings);
        return matcher.results().map(result -> result.group(1)).toList();
    }

    private static List<Path> dockerfiles() throws IOException {
        try (Stream<Path> paths = Files.walk(BACKEND, 2)) {
            return paths.filter(path -> path.getFileName().toString().equals("Dockerfile"))
                    .sorted()
                    .toList();
        }
    }

    @Test
    @DisplayName("どの Dockerfile も、include された全モジュールの build.gradle.kts を写す")
    void copiesEveryModuleBuildScript() throws IOException {
        List<String> modules = modules();
        assertThat(modules).as("include の一覧を読めていない").isNotEmpty();

        for (Path dockerfile : dockerfiles()) {
            String content = Files.readString(dockerfile, StandardCharsets.UTF_8);
            for (String module : modules) {
                String expected = "COPY " + module + "/build.gradle.kts";
                assertThat(content)
                        .as("%s が %s の build.gradle.kts を写していない"
                                + "（イメージが 1 つも作れなくなる）",
                                BACKEND.relativize(dockerfile), module)
                        .contains(expected);
            }
        }
    }

    @Test
    @DisplayName("Dockerfile はサービスの数だけある（写し忘れを数で気づく）")
    void hasOneDockerfilePerService() throws IOException {
        // shared・contract-tests・acceptance-tests は配らないので Dockerfile を持たない。
        long services = modules().stream()
                .filter(module -> module.endsWith("ms"))
                .count();
        assertThat(dockerfiles()).hasSize((int) services);
    }
}
