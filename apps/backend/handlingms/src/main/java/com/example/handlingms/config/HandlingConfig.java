package com.example.handlingms.config;

import com.example.handlingms.application.port.CargoSnapshotFinder;
import com.example.handlingms.infrastructure.booking.RestCargoSnapshotFinder;
import com.example.shared.auth.AuthenticatedUserFilter;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class HandlingConfig {

    /**
     * Gateway が付けた利用者ヘッダを必須とする（ADR-007）。
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
     * 追跡番号から貨物を引く ACL（[ADR-023] 決定 2）。
     *
     * <p><strong>システム主体として名乗る</strong>（[ADR-019] 後日談 3）。名乗りは
     * アダプタが持つ——ここで組み立てると、呼び出し側ごとに名乗りが分かれる。
     */
    @Bean
    public CargoSnapshotFinder cargoSnapshotFinder(
            @Value("${app.booking-service.base-url}") String baseUrl) {
        return new RestCargoSnapshotFinder(
                RestClient.builder().baseUrl(baseUrl).requestFactory(bookingRequestFactory())
                        .build());
    }

    /**
     * bookingms への呼び出しに期限を置く。
     *
     * <p><strong>「落ちている」と「遅い」は別の障害である。</strong>応答が返らないだけの
     * 状態では、handlingms のスレッドが照会 1 件につき 1 本ずつ埋まり、
     * <strong>照会と無関係な荷役の一覧まで巻き込んで止まる</strong>。落ちる範囲を
     * bookingms に閉じるために期限を置く。
     *
     * <p><strong>再送はしない。</strong>照会の遅さは荷役作業員の待ち時間に直結し、
     * 遅い相手に送り直すと詰まりが増える。
     */
    private static ClientHttpRequestFactory bookingRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }
}
