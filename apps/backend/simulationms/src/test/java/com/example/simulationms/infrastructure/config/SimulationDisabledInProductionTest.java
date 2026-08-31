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

    /**
     * <strong>許可した環境以外では起動しない</strong>（IT14 レビューで名簿の向きを反転）。
     *
     * <p>「本番相当」を名簿にすると、<strong>載せ忘れた環境で動いてしまう</strong>——
     * 環境を増やす人がこのクラスを開く理由は無いので、載せ忘れは必ず起きる。
     * 未知のプロファイル（{@code preprod} など）も落ちることを、ここで確かめる。
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {"product", "production", "prod", "preprod", "本番"})
    @DisplayName("許可していない環境で有効にすると、起動そのものが失敗する")
    void refusesToStartOutsideAllowedProfiles(String profile) {
        runner.withPropertyValues("app.simulation.enabled=true")
                .withInitializer(context -> context.getEnvironment()
                        .setActiveProfiles(profile))
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining(profile));
    }

    @Test
    @DisplayName("許可していない環境でも、無効なら起動する")
    void startsInProductionWhenDisabled() {
        runner.withPropertyValues("app.simulation.enabled=false")
                .withInitializer(context -> context.getEnvironment()
                        .setActiveProfiles("product"))
                .run(context -> assertThat(context).hasNotFailed());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"local", "dev", "staging"})
    @DisplayName("実行を許した環境では、有効にして起動できる")
    void startsInAllowedProfiles(String profile) {
        runner.withPropertyValues("app.simulation.enabled=true")
                .withInitializer(context -> context.getEnvironment()
                        .setActiveProfiles(profile))
                .run(context -> assertThat(context).hasNotFailed());
    }

    /** プロファイル未指定（開発機の素の起動）は許す。動かないとこの仕組み自体を作れない。 */
    @Test
    @DisplayName("プロファイル未指定では、有効にして起動できる")
    void startsWithoutAnyProfile() {
        runner.withPropertyValues("app.simulation.enabled=true")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
