package com.example.cargotracker.simulation.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
        assertThat(config()).contains("${CARGOTRACKER_SIMULATION_ENABLED:");
        assertThat(config()).contains("${GATEWAY_URL:");
    }

    @Test
    @DisplayName("工程の宛先は Gateway（サービスを直接指さない）")
    void pointsAtTheGateway() throws IOException {
        // 直接指すと認可を通らず、確かめたいものが変わる（[ADR-0020] 決定 2）。
        assertThat(config()).contains("gateway-url:");
    }
}
