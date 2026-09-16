package com.example.cargotracker.shared.conventions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code simulationms} は他サービスの中身に依存しない（[ADR-0020] 決定 1）。
 *
 * <p><b>呼ぶのは API だけである。</b> 他サービスのパッケージ（ドメイン・投影・
 * マッパー）を直接使うと、<b>人が操作するのと同じ経路</b>という前提が崩れる——
 * 内側を知っているものは、認可も入力検証も飛び越えられる。</p>
 *
 * <p><b>逆向きも見る。</b> 業務サービスが `simulationms` を参照したら、業務の実装が
 * シミュレーションの存在を知ることになる。本番で起動しないものに業務が依存する形は
 * 作らない。</p>
 */
class SimulationServiceIsSeparateTest {

    private static final String SIMULATION = "simulationms";
    private static final String SIMULATION_PACKAGE =
            "com.example.cargotracker.simulation";

    /** 業務サービス。`shared` は共有カーネルなので双方が使ってよい。 */
    private static final List<String> BUSINESS_PACKAGES = List.of(
            "com.example.cargotracker.booking",
            "com.example.cargotracker.routing",
            "com.example.cargotracker.tracking",
            "com.example.cargotracker.handling",
            "com.example.cargotracker.billing",
            "com.example.cargotracker.auth",
            "com.example.cargotracker.gateway");

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
            return paths.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.toString().replace('\\', '/').contains("/src/main/java/"))
                    .toList();
        }
    }

    @Test
    @DisplayName("[ADR-0020] simulationms は他サービスの中身を参照しない（API だけを叩く）")
    void simulationDoesNotDependOnBusinessPackages() throws IOException {
        List<String> offenders = new ArrayList<>();
        int scanned = 0;
        for (Path path : mainSources()) {
            if (!path.toString().replace('\\', '/').contains("/" + SIMULATION + "/")) {
                continue;
            }
            scanned++;
            String source = Files.readString(path, StandardCharsets.UTF_8);
            for (String business : BUSINESS_PACKAGES) {
                if (source.contains("import " + business + ".")) {
                    offenders.add(path.getFileName() + " → " + business);
                }
            }
        }

        assertThat(scanned)
                .as("simulationms の本番ソースを 1 つも読めていない（検査が空振りしている）")
                .isPositive();
        assertThat(offenders)
                .as("**呼ぶのは API だけ。** 内側を知っているものは認可も入力検証も"
                        + "飛び越えられ、「人が操作するのと同じ経路」という前提が崩れる")
                .isEmpty();
    }

    @Test
    @DisplayName("[ADR-0020] 業務サービスは simulationms を参照しない（逆向きの依存も作らない）")
    void businessServicesDoNotDependOnSimulation() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path path : mainSources()) {
            String normalized = path.toString().replace('\\', '/');
            if (normalized.contains("/" + SIMULATION + "/")) {
                continue;
            }
            String source = Files.readString(path, StandardCharsets.UTF_8);
            if (source.contains("import " + SIMULATION_PACKAGE + ".")) {
                offenders.add(path.getFileName().toString());
            }
        }

        assertThat(offenders)
                .as("**本番で起動しないものに業務が依存する形を作らない。** 業務の実装が"
                        + "シミュレーションの存在を知ると、環境で振る舞いが変わる余地が生まれる")
                .isEmpty();
    }
}
