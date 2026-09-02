package com.example.simulationms;

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
 * シミュレーションが「本番と同じ経路」を踏むことを、構造で固定する（[ADR-030] 決定 2）。
 *
 * <p><strong>専用の書き込み経路を作ると、この仕組みの意味が失われる。</strong>
 * 検出できなくなるのは、まさに「シミュレーションは通るのに実際の操作は通らない」状態である。
 *
 * <p>ArchUnit では見えない性質なので、ソースを読んで確かめる。ArchUnit が見るのは型の
 * 依存であり、<strong>文字列で書かれた URL の向き先は映らない</strong>。
 */
@DisplayName("シミュレーションの経路")
class SimulationArchitectureTest {

    private static final Path SOURCE = Path.of("src/main/java").toAbsolutePath().normalize();

    /**
     * 使ってはいけない経路と名乗り。
     *
     * <p>いずれも「認可を素通りする」入口である。人のロールでは開かれていないため、
     * ここを使うと実利用者が踏めない経路をシミュレーションだけが踏むことになる。
     */
    private static final List<String> FORBIDDEN = List.of(
            "/api/v1/internal",
            "shipper-snapshots",
            "by-tracking-number",
            "billing-snapshot",
            "system:");

    @Test
    @DisplayName("内部 API と system 名乗りを、どこからも参照していない")
    void doesNotReferenceInternalApis() throws IOException {
        List<String> sources = javaSources();

        assertThat(sources)
                .as("ソースを 1 つも読めていない場合、この検査は何も守らない")
                .isNotEmpty();

        List<String> violations = new ArrayList<>();
        for (String source : sources) {
            for (String forbidden : FORBIDDEN) {
                if (source.contains(forbidden)) {
                    violations.add(forbidden);
                }
            }
        }

        assertThat(violations)
                .as("内部 API または system 名乗りを使っている。認可を素通りする経路を"
                        + "新設すると、実利用者が踏めない経路をシミュレーションだけが踏む")
                .isEmpty();
    }

    @Test
    @DisplayName("業務 API を呼ぶ出口は 1 ポートだけである")
    void hasExactlyOneOutboundPort() throws IOException {
        Path ports = SOURCE.resolve(
                "com/example/simulationms/application/internal/outboundservices/acl");

        try (Stream<Path> files = Files.list(ports)) {
            List<String> interfaces = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(SimulationArchitectureTest::declaresInterface)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();

            assertThat(interfaces)
                    .as("出口を増やすと、そのうち 1 本が内部 API を向いた時点で"
                            + "「本番と同じ経路」が崩れる。増やす前に ADR-030 決定 2 を読み直す")
                    .containsExactly("BusinessGateway.java");
        }
    }

    private static boolean declaresInterface(Path path) {
        try {
            return Files.readString(path).contains("public interface ");
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static List<String> javaSources() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCE)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .map(SimulationArchitectureTest::read)
                    .map(SimulationArchitectureTest::withoutComments)
                    .toList();
        }
    }

    /**
     * コメントを落とす。
     *
     * <p><strong>禁じた経路を「なぜ使わないか」はコメントに書く。</strong>
     * コメントごと見ると、理由を書いた場所が違反として挙がり、
     * 理由を書けなくなる——検査が説明を追い出してはいけない。
     */
    private static String withoutComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
