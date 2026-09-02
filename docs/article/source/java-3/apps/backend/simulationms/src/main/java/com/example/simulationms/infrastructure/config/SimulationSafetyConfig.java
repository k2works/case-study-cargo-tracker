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
     * シミュレーションを実行してよいと<strong>明示した</strong>環境。
     *
     * <p><strong>名簿の向きを反転させた（IT14 レビュー）。</strong>当初は「本番相当」を
     * 名簿にしていたが、それだと<strong>載せ忘れた環境で動いてしまう</strong>——
     * 環境を増やす人がこのクラスを開く理由は無いので、載せ忘れは必ず起きる。
     * 許可する側の名簿にすれば、載せ忘れは「動かない」側に倒れる。
     *
     * <p>プロファイル未指定（ローカルの素の起動）も許可に含める。開発機で動かないと、
     * この仕組み自体を作れない。
     */
    private static final List<String> SIMULATION_ALLOWED_PROFILES =
            List.of("local", "dev", "development", "integration", "test", "staging");

    @Bean
    public InitializingBean simulationProductionGuard(Environment environment,
            @Value("${app.simulation.enabled:false}") boolean enabled) {
        return () -> {
            if (!enabled) {
                return;
            }
            List<String> active = Arrays.asList(environment.getActiveProfiles());
            if (active.isEmpty() || active.stream().allMatch(SIMULATION_ALLOWED_PROFILES::contains)) {
                return;
            }
            throw new IllegalStateException(
                    "この環境（%s）でシミュレーションを有効にはできません。"
                            .formatted(String.join(", ", active))
                            + "APP_SIMULATION_ENABLED を false にするか、"
                            + "実行を許す環境なら SimulationSafetyConfig の許可一覧に足してください");
        };
    }
}
