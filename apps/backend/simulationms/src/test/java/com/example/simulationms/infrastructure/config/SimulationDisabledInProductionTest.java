package com.example.simulationms.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 本番相当の環境では、シミュレーションを有効にしたまま起動できない（[ADR-030] 決定 4）。
 *
 * <p><strong>設定を読んで実行時に断る形にはしない。</strong>実行時に断ると、設定の
 * 読み違いは「実行しようとして初めて」分かる。起動で落とせば、配備した時点で分かる。
 */
@DisplayName("本番でのシミュレーション")
class SimulationDisabledInProductionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of())
            .withUserConfiguration(SimulationSafetyConfig.class);

    @Test
    @DisplayName("本番プロファイルで有効にすると、起動そのものが失敗する")
    void refusesToStartInProduction() {
        runner.withPropertyValues("app.simulation.enabled=true")
                .withPropertyValues("spring.profiles.active=product")
                .withInitializer(context -> context.getEnvironment()
                        .setActiveProfiles("product"))
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("本番"));
    }

    @Test
    @DisplayName("本番プロファイルでも、無効なら起動する")
    void startsInProductionWhenDisabled() {
        runner.withPropertyValues("app.simulation.enabled=false")
                .withInitializer(context -> context.getEnvironment()
                        .setActiveProfiles("product"))
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("本番でない環境では、有効にして起動できる")
    void startsOutsideProduction() {
        runner.withPropertyValues("app.simulation.enabled=true")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
