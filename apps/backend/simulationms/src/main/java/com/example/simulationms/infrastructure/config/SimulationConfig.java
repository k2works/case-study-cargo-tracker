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
