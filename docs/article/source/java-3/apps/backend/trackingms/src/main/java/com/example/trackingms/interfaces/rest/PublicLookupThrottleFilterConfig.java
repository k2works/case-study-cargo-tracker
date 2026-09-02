package com.example.trackingms.interfaces.rest;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
class PublicLookupThrottleFilterConfig {

    /**
     * 上限を掛ける対象の接頭辞。
     *
     * <p><strong>設定にしない。</strong>どの経路が認証の外に居るかは
     * [ADR-024] 決定 6 が決めたことであり、配備先ごとに変えてよいものではない。
     * 設定で広げれば ヘルスチェックまで掛かり、狭めれば公開経路が無防備になる。
     */
    @SuppressWarnings("java:S1075")
    private static final String PUBLIC_PATH_PREFIX = "/api/v1/public/";

    /**
     * 公開の追跡照会に上限を置く（[ADR-024] 決定 6）。
     *
     * <p>認証が無い唯一の業務経路であり、追跡番号は日付が既知なら 4 桁しかない。
     *
     * <p><strong>ヘルスチェックには掛からない。</strong>フィルタが接頭辞で絞っている
     * ——一律に掛けると、過負荷のときに liveness が 429 を返して再起動ループになる。
     */
    @Bean
    FilterRegistrationBean<PublicLookupThrottleFilter> publicLookupThrottleFilter(
            @Value("${app.public-lookup.limit-per-minute:30}") int limitPerMinute,
            Clock clock) {
        FilterRegistrationBean<PublicLookupThrottleFilter>
                registration = new FilterRegistrationBean<>(
                        new PublicLookupThrottleFilter(
                                PUBLIC_PATH_PREFIX, limitPerMinute, clock));
        // 認証フィルタの直後に置く。公開経路は認証を通らないので、順序は実質ここが先頭になる
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }
}
