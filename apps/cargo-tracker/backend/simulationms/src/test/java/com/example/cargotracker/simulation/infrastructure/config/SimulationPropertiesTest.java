package com.example.cargotracker.simulation.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 本番では実行しない（US33 §受入基準 4 / [ADR-0020]）。
 *
 * <p><b>設定を読む検査は入力を宣言する。</b> 宣言しないと Gradle が UP-TO-DATE と
 * 判断して走らず、壊しても緑になる（IT10 の教訓）——`build.gradle.kts` で
 * `application.yml` を入力に加えてある。</p>
 */
class SimulationPropertiesTest {

    private static final Path CONFIG = Path.of("src/main/resources/application.yml");

    private static String config() throws IOException {
        return Files.readString(CONFIG, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("US33 §4: 既定は無効（本番で静かに動き出さない）")
    void defaultsToDisabled() throws IOException {
        assertThat(config())
                .as("**既定を有効にしない。** 設定を忘れた環境で実データに紛れる貨物を作る")
                .contains("enabled: ${CARGOTRACKER_SIMULATION_ENABLED:false}");
    }

    @Test
    @DisplayName("環境変数の名前を推測させない（外したときに静かに無効にならない）")
    void namesTheEnvironmentVariableExplicitly() throws IOException {
        // **リラックスバインディングに頼らない。** どの環境変数名に対応するかは
        // 規則を知らないと読めず、外すと「既定のまま静かに無効」になって気づけない。
        // **設定を足したら、ここも足す**——1 本ずつ書くと、次に足した設定が漏れる。
        assertThat(config())
                .contains("${CARGOTRACKER_SIMULATION_ENABLED:")
                .contains("${GATEWAY_URL:")
                .contains("${CARGOTRACKER_SIMULATION_SCHEDULE_ENABLED:")
                .contains("${CARGOTRACKER_SIMULATION_SCHEDULE_INTERVAL:")
                .contains("${CARGOTRACKER_SIMULATION_SCHEDULE_MAX_CONCURRENT:")
                .contains("${CARGOTRACKER_SIMULATION_SCHEDULE_EXCEPTION_RATIO:");
    }

    @Test
    @DisplayName("US36 §6: 継続実行の既定は無効（実行できる環境＝流し続けてよい環境にしない）")
    void scheduleDefaultsToDisabled() throws IOException {
        // **流し続ける側は業務を止めうる**（この局面に固有の危険 3）。
        // 実行そのものの許可とは別の段にする。
        assertThat(config())
                .contains("enabled: ${CARGOTRACKER_SIMULATION_SCHEDULE_ENABLED:false}");
    }

    @Test
    @DisplayName("US36 §2: 実行間隔の既定は 0 でない（間を空けないと業務が止まる）")
    void scheduleIntervalIsNotZeroByDefault() throws IOException {
        assertThat(config())
                .doesNotContain("CARGOTRACKER_SIMULATION_SCHEDULE_INTERVAL:0")
                .contains("CARGOTRACKER_SIMULATION_SCHEDULE_INTERVAL:30s");
    }

    @Test
    @DisplayName("工程の宛先は Gateway（サービスを直接指さない）")
    void pointsAtTheGateway() throws IOException {
        // 直接指すと認可を通らず、確かめたいものが変わる（[ADR-0020] 決定 2）。
        assertThat(config()).contains("gateway-url:");
    }

    /** 設定に書かれた宛先。<b>1 つであること</b>が「Gateway だけを指す」の中身。 */
    private static final Pattern URL_IN_CONFIG = Pattern.compile("https?://[^\\s\"}]+");

    /** 本番コードに直書きされた宛先。<b>形を問わず全部拾ってから</b>判定する。 */
    private static final Pattern URL_IN_CODE = Pattern.compile("\"https?://");

    @Test
    @DisplayName("[ADR-0020] 決定 2: 宛先は設定の 1 つだけ（サービスの URL を増やさない）")
    void declaresExactlyOneDestination() throws IOException {
        // **「gateway-url: がある」では何も確かめていない。** その隣に
        // `booking-url:` を足しても緑のままで、Gateway を通さない経路が
        // 静かに増える（IT16 のレビュー N11）。**宛先を数え上げる。**
        List<String> destinations = URL_IN_CONFIG.matcher(config()).results()
                .map(match -> match.group()).toList();

        assertThat(destinations)
                .as("設定に書かれた宛先: %s", destinations)
                .hasSize(1);
        assertThat(destinations.get(0)).startsWith("http://localhost:");
    }

    @Test
    @DisplayName("[ADR-0020] 決定 2: 本番コードは宛先を直書きしない")
    void hardCodesNoDestination() throws IOException {
        // 設定を 1 つに絞っても、コードに直書きされていれば意味がない。
        // **走査してから判定する**——「1 本ずつ思いついた場所を見る」形にすると、
        // 次に足したクラスが漏れる。
        List<Path> offenders;
        try (var sources = Files.walk(Path.of("src/main/java"))) {
            offenders = sources.filter(path -> path.toString().endsWith(".java"))
                    .filter(SimulationPropertiesTest::containsLiteralUrl)
                    .toList();
        }

        assertThat(offenders)
                .as("宛先を直書きしているファイル: %s", offenders)
                .isEmpty();
    }

    private static boolean containsLiteralUrl(Path path) {
        try {
            return URL_IN_CODE.matcher(Files.readString(path, StandardCharsets.UTF_8)).find();
        } catch (IOException e) {
            throw new IllegalStateException("読めないファイルがある: " + path, e);
        }
    }

    @Test
    @DisplayName("検査が空振りしていない（走査の対象が実在する）")
    void scansSomething() throws IOException {
        long sources;
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            sources = paths.filter(path -> path.toString().endsWith(".java")).count();
        }
        assertThat(sources)
                .as("**走査の対象が 0 件なら、上の検査は何も確かめていない**")
                .isGreaterThan(10);
    }
}
