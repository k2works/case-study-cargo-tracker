package com.example.simulationms.infrastructure.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 本番相当の環境でシミュレーションが動かないことを、起動時に保証する（[ADR-030] 決定 4）。
 *
 * <p><strong>設定を読んで実行時に断る形にはしない。</strong>実行時に断ると、設定の
 * 読み違いは「誰かが実行しようとして初めて」分かる。起動で落とせば配備した時点で分かる。
 *
 * <p>破られたら、本番に {@code SIM-} の荷主と貨物が生まれる。除外が効いていても、
 * <strong>実在しない輸送の記録が本番の DB に残ること自体</strong>が業務の事故である。
 */
@Configuration
public class SimulationSafetyConfig {

    /**
     * 本番相当とみなすプロファイル。
     *
     * <p>名簿だが、<strong>載せ忘れれば「本番なのに動く」側に倒れる</strong>。
     * 環境を増やすときは、ここに足すことをデプロイの手順に含める。
     */
    private static final List<String> PRODUCTION_PROFILES = List.of("product", "production", "prod");

    @Bean
    public InitializingBean simulationProductionGuard(Environment environment,
            @Value("${app.simulation.enabled:false}") boolean enabled) {
        return () -> {
            if (!enabled) {
                return;
            }
            List<String> active = Arrays.asList(environment.getActiveProfiles());
            if (active.stream().anyMatch(PRODUCTION_PROFILES::contains)) {
                throw new IllegalStateException(
                        "本番相当の環境（%s）でシミュレーションを有効にはできません。"
                                .formatted(String.join(", ", active))
                                + "APP_SIMULATION_ENABLED を false にしてください");
            }
        };
    }
}
