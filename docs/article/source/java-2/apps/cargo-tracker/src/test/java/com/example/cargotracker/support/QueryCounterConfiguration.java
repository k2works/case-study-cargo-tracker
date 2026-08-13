package com.example.cargotracker.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * {@link QueryCounter} をテストの構成に載せる。
 *
 * <p>MyBatis は {@code Interceptor} 型の Bean を自動で取り込む
 * （{@code MybatisAutoConfiguration}）。<strong>本番の構成には入らない</strong> —
 * 本クラスは {@code src/test} にのみ存在する。
 */
@TestConfiguration
public class QueryCounterConfiguration {

    @Bean
    public QueryCounter queryCounter() {
        return new QueryCounter();
    }
}
