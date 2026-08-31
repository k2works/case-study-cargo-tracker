package com.example.simulationms.infrastructure.config;

import com.example.shared.auth.AuthenticatedUserFilter;
import com.example.simulationms.domain.repository.SimulationRunRepository;
import com.example.simulationms.infrastructure.repositories.MyBatisSimulationRunRepository;
import com.example.simulationms.infrastructure.repositories.SimulationRunMapper;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
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

    /**
     * 業務タイムゾーンの時計。
     *
     * <p><strong>UTC で「今日」を決めない。</strong>シミュレーションが作る予約の期限も、
     * 実行の開始時刻も、業務の暦で読む。
     */
    @Bean
    public SimulationRunRepository simulationRunRepository(SimulationRunMapper mapper) {
        return new MyBatisSimulationRunRepository(mapper);
    }

    @Bean
    public Clock clock(@Value("${app.business-time-zone:Asia/Tokyo}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
