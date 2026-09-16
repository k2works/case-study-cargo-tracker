package com.example.cargotracker.simulation.infrastructure.config;

import com.example.cargotracker.simulation.application.SimulationRunner;
import com.example.cargotracker.simulation.application.SimulationService;
import com.example.cargotracker.simulation.infrastructure.api.GatewayBusinessApi;
import com.example.cargotracker.simulation.infrastructure.api.GatewayCalls;
import com.example.cargotracker.simulation.infrastructure.api.GatewayChainReadiness;
import com.example.cargotracker.simulation.infrastructure.api.GatewayTokens;
import com.example.cargotracker.simulation.infrastructure.persistence.SimulationRunMapper;
import java.time.Clock;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** 実行の組み立て（[ADR-0020]）。 */
@Configuration
@EnableConfigurationProperties({ SimulationProperties.class, SimulationScheduleProperties.class })
@org.springframework.scheduling.annotation.EnableScheduling
public class SimulationConfig {

    /**
     * 同時に走らせる本数。<b>シナリオの数から導く</b>。
     *
     * <p><b>数を書き写さない。</b> 二重実行はそもそも断る（US33 §受入基準 5）
     * ので、<b>同時に走りうるのは多くてもシナリオの数</b>である——書き写すと、
     * シナリオを足したときに片方だけが直る。</p>
     *
     * <p><b>足りないと「実行中なのに進まない」が起きる。</b> 実行は
     * <b>記録してから走らせる</b>（読み口に現れないと画面が「始まっていない」と
     * 読むため）。糸が足りないと、記録だけ「実行中」で置かれたまま順番待ちに
     * なり、S92 には進まない実行が並ぶ。まとめて流せるようにした以上、
     * <b>選べる数だけ走れる</b>必要がある。</p>
     */
    private static final int CONCURRENT_RUNS =
            com.example.cargotracker.simulation.domain.model.valueobjects.Scenario
                    .values().length;

    /** Gateway を叩く口。<b>Gateway を通る</b>（[ADR-0020] 決定 2）。 */
    @Bean
    public RestClient gatewayRestClient(SimulationProperties properties) {
        return RestClient.builder().baseUrl(properties.gatewayUrl()).build();
    }

    /**
     * 実行を走らせる糸。
     *
     * <p><b>要求の糸で走らせない。</b> 標準シナリオは連鎖の追いつきを待ちながら
     * 進むので、終わるまで返さない形にすると要求が先に切れる。</p>
     */
    @Bean(destroyMethod = "shutdown")
    public java.util.concurrent.ExecutorService simulationExecutor() {
        return Executors.newFixedThreadPool(CONCURRENT_RUNS,
                Thread.ofPlatform().name("simulation-", 0).factory());
    }

    @Bean
    public SimulationService simulationService(SimulationRunMapper runs,
            SimulationProperties properties, RestClient gatewayRestClient,
            Executor simulationExecutor, Clock clock) {
        return new SimulationService(runs, properties, (input, listener) -> {
            // **実行ごとに作る。** トークンも作った識別子も実行の中でしか
            // 意味を持たない（[ADR-0020] 決定 2）。
            GatewayCalls calls = new GatewayCalls(gatewayRestClient,
                    new GatewayTokens(gatewayRestClient));
            return new SimulationRunner(new GatewayBusinessApi(calls, input, clock),
                    new GatewayChainReadiness(calls), SimulationRunner.realSleeper(),
                    clock, listener);
        }, simulationExecutor, clock);
    }

    /** 継続実行の稼働（US36）。 */
    @Bean
    public com.example.cargotracker.simulation.application.SimulationScheduleService
            simulationScheduleService(
            com.example.cargotracker.simulation.infrastructure.persistence
                    .SimulationScheduleMapper schedules,
            SimulationService simulationService,
            SimulationScheduleProperties properties, Clock clock) {
        return new com.example.cargotracker.simulation.application.SimulationScheduleService(
                schedules, simulationService, properties, clock);
    }
}
