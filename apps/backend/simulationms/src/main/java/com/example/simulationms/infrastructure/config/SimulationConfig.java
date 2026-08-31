package com.example.simulationms.infrastructure.config;

import com.example.shared.auth.AuthenticatedUserFilter;
import com.example.simulationms.application.internal.outboundservices.acl.BusinessGateway;
import com.example.simulationms.domain.repository.SimulationRunRepository;
import com.example.simulationms.infrastructure.acl.RestBusinessGateway;
import com.example.simulationms.infrastructure.acl.SimulationUsers;
import com.example.simulationms.infrastructure.repositories.MyBatisSimulationRunRepository;
import com.example.simulationms.infrastructure.repositories.SimulationRunMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@org.springframework.boot.context.properties.EnableConfigurationProperties(SimulationUserProperties.class)
public class SimulationConfig {

    /**
     * Gateway が付けた利用者ヘッダを必須とする（[ADR-007]）。
     *
     * <p>認可を書き忘れたエンドポイントが無認証で開くことを、認可判定より前で塞ぐ。
     * 公開エンドポイントを持たないため、除外はヘルスチェックだけである。
     */
    @Bean
    public FilterRegistrationBean<AuthenticatedUserFilter> authenticatedUserFilter() {
        FilterRegistrationBean<AuthenticatedUserFilter> registration =
                new FilterRegistrationBean<>(new AuthenticatedUserFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /** 実行の記録の永続化。 */
    @Bean
    public SimulationRunRepository simulationRunRepository(SimulationRunMapper mapper) {
        return new MyBatisSimulationRunRepository(mapper);
    }

    /** 継続実行のセッションの永続化（US37）。 */
    @Bean
    public com.example.simulationms.domain.repository.ContinuousRunSessionRepository
            continuousRunSessionRepository(
            com.example.simulationms.infrastructure.repositories.ContinuousRunSessionMapper
                    mapper) {
        return new com.example.simulationms.infrastructure.repositories
                .MyBatisContinuousRunSessionRepository(mapper);
    }

    /**
     * 継続実行を走らせるスレッド。
     *
     * <p><strong>数を上限に合わせて絞る。</strong>絞らないと、上限の判定をすり抜けた
     * ときにスレッドが際限なく増える——上限は 2 か所で守る方が安い。
     *
     * <p><strong>ヘルスチェックはこのプールを通らない。</strong>Web の要求は Tomcat の
     * スレッドで処理される。ここが埋まっても liveness / readiness は影響を受けない
     * （[ADR-031] 決定 3・IT7 の再発防止）。
     */
    @Bean(destroyMethod = "shutdown")
    public java.util.concurrent.ExecutorService continuousRunExecutor() {
        return java.util.concurrent.Executors.newFixedThreadPool(
                com.example.simulationms.domain.model.valueobjects.ContinuousRunPolicy
                        .MAX_CONCURRENT_LIMIT,
                Thread.ofPlatform().name("simulation-run-", 0).daemon().factory());
    }

    /** 継続実行の 1 件を別スレッドで走らせる出口（US37）。 */
    @Bean
    public com.example.simulationms.application.internal.commandservices.ContinuousRunner
            continuousRunner(
            com.example.simulationms.application.internal.commandservices.RunSimulationUseCase
                    runSimulation,
            java.util.concurrent.ExecutorService continuousRunExecutor) {
        return new com.example.simulationms.infrastructure.scheduling.AsyncContinuousRunner(
                runSimulation, continuousRunExecutor);
    }

    /** 継続実行の刻み（[ADR-031] 決定 2）。**外部のジョブ基盤を持ち込まない**。 */
    @Bean
    public com.example.simulationms.application.internal.commandservices.ContinuousRunScheduler
            continuousRunScheduler(
            com.example.simulationms.domain.repository.ContinuousRunSessionRepository sessions,
            com.example.simulationms.application.internal.commandservices.ContinuousRunner runner,
            Clock clock) {
        return new com.example.simulationms.application.internal.commandservices
                .ContinuousRunScheduler(sessions, runner, clock);
    }

    /**
     * 刻みを回す。
     *
     * <p>シミュレーションを無効にした環境では登録しない——設定の名前が守っている
     * つもりにならないよう、<strong>実際に何も動かない</strong>形にする。
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "app.simulation.enabled", havingValue = "true", matchIfMissing = true)
    public com.example.simulationms.infrastructure.scheduling.ContinuousRunTrigger
            continuousRunTrigger(
            com.example.simulationms.application.internal.commandservices.ContinuousRunScheduler
                    scheduler) {
        return new com.example.simulationms.infrastructure.scheduling.ContinuousRunTrigger(
                scheduler);
    }

    /**
     * 業務タイムゾーンの時計。
     *
     * <p><strong>UTC で「今日」を決めない。</strong>シミュレーションが作る予約の期限も、
     * 実行の開始時刻も、業務の暦で読む。
     */
    @Bean
    public Clock clock(@Value("${app.business-time-zone:Asia/Tokyo}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }

    /**
     * 工程を踏む利用者の名簿（[ADR-030] 決定 2）。
     *
     * <p>環境ごとに利用者は変わるため設定から受け取る。<strong>合言葉はコードに書かない。</strong>
     */
    @Bean
    public SimulationUsers simulationUsers(SimulationUserProperties properties) {
        return SimulationUsers.of(properties.usernames(), properties.password());
    }

    /**
     * シナリオを 1 本流す（US34）。
     *
     * <p><strong>{@code @Transactional} を置かない</strong>（[ADR-030] 決定 5）。
     * まとめて 1 つのトランザクションにすると、失敗したときにどこまで進んだかの記録ごと消える。
     */
    @Bean
    public com.example.simulationms.application.internal.commandservices.RunSimulationUseCase
            runSimulationUseCase(SimulationRunRepository runs, BusinessGateway business,
            Clock clock) {
        return new com.example.simulationms.application.internal.commandservices
                .RunSimulationUseCase(runs, business, clock);
    }

    /**
     * 業務 API を呼ぶ唯一の出口（[ADR-030] 決定 2）。
     *
     * <p><strong>期限を置く。</strong>応答が返らないだけの状態でも、工程は 12 個あり
     * 1 つ詰まると実行そのものが終わらない。落ちる範囲をその工程に閉じる。
     *
     * <p><strong>再送はしない。</strong>業務データを作る呼び出しであり、
     * 送り直すと同じ予約が 2 件生まれる。
     */
    @Bean
    public BusinessGateway businessGateway(
            @Value("${app.gateway.base-url}") String baseUrl, SimulationUsers users,
            Clock clock) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return new RestBusinessGateway(
                RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build(),
                users, clock);
    }
}
