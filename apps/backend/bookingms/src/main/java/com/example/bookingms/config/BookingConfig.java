package com.example.bookingms.config;

import com.example.bookingms.application.internal.AssignRouteUseCase;
import com.example.bookingms.application.internal.BookCargoUseCase;
import com.example.bookingms.application.internal.EditShipperUseCase;
import com.example.bookingms.application.internal.RegisterShipperUseCase;
import com.example.bookingms.application.internal.RequestConsultationUseCase;
import com.example.bookingms.application.internal.RequestRoutingUseCase;
import com.example.bookingms.application.internal.SearchCargoUseCase;
import com.example.bookingms.application.internal.SearchShipperUseCase;
import com.example.bookingms.application.port.CargoRepository;
import com.example.bookingms.application.port.LocationRepository;
import com.example.bookingms.application.port.RouteCandidateFinder;
import com.example.bookingms.application.port.ShipperRepository;
import com.example.bookingms.infrastructure.routing.RestRouteCandidateFinder;
import com.example.shared.auth.AuthenticatedUserFilter;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class BookingConfig {

    /**
     * Gateway が付けた利用者ヘッダを必須とする（ADR-007）。
     *
     * <p>bookingms に公開エンドポイントは無い。認可判定より前に弾くため、順序を最上位にする。
     */
    @Bean
    public FilterRegistrationBean<AuthenticatedUserFilter> authenticatedUserFilter() {
        FilterRegistrationBean<AuthenticatedUserFilter> registration =
                new FilterRegistrationBean<>(new AuthenticatedUserFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /** 業務日付は業務タイムゾーンで判断する。UTC で判断すると「当日」の扱いがずれる時間帯ができる。 */
    @Bean
    public Clock clock(@Value("${app.business-time-zone:Asia/Tokyo}") String zoneId) {
        return Clock.system(ZoneId.of(zoneId));
    }

    /**
     * 経路候補を取りに行く先（[ADR-019]）。
     *
     * <p><strong>接続先に既定値を持たせない。</strong>持たせると設定漏れが実行時まで
     * 表面化せず、しかも IT5 では既定値が bookingms 自身を指していたため「経路の確定だけが
     * 必ず失敗する」状態になっていた。値は {@code application.yml} と環境変数で与える。
     *
     * <p><strong>利用者ヘッダ（[ADR-007]）は伝播しない。</strong>この呼び出しは「システムが
     * 経路候補を引く」ものであり、利用者の代理ではない。伝播すると、bookingms の中で完結する
     * 処理（確定時の再検証）が呼び出し元のロールに依存する。
     */
    @Bean
    public RouteCandidateFinder routeCandidateFinder(
            @Value("${app.routing-service.base-url}") String baseUrl,
            LocationRepository locations) {
        return new RestRouteCandidateFinder(
                RestClient.builder().baseUrl(baseUrl).requestFactory(routingRequestFactory())
                        .build(),
                locations);
    }

    /**
     * routingms への呼び出しに期限を置く。
     *
     * <p><strong>「落ちている」と「遅い」は別の障害である。</strong>[ADR-019] のネガティブは
     * 「routingms が落ちていると確定できない」と書いたが、応答が返らないだけの状態では
     * bookingms のスレッドが確定 1 件につき 1 本ずつ埋まり、<strong>経路と無関係な予約一覧や
     * 荷主登録まで巻き込んで止まる</strong>。落ちる範囲を routingms に閉じるために期限を置く。
     *
     * <p><strong>再送はしない。</strong>再検証の遅さは確定操作の遅さに直結し、遅い相手に
     * 送り直すと詰まりが増える。
     */
    private static ClientHttpRequestFactory routingRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }

    @Bean
    public RequestConsultationUseCase requestConsultationUseCase(CargoRepository cargoes) {
        return new RequestConsultationUseCase(cargoes);
    }

    @Bean
    public AssignRouteUseCase assignRouteUseCase(CargoRepository cargoes,
            LocationRepository locations, RouteCandidateFinder routeCandidates) {
        return new AssignRouteUseCase(cargoes, locations, routeCandidates);
    }

    @Bean
    public RegisterShipperUseCase registerShipperUseCase(ShipperRepository repository) {
        return new RegisterShipperUseCase(repository);
    }

    @Bean
    public EditShipperUseCase editShipperUseCase(ShipperRepository repository) {
        return new EditShipperUseCase(repository);
    }

    @Bean
    public SearchShipperUseCase searchShipperUseCase(ShipperRepository repository) {
        return new SearchShipperUseCase(repository);
    }

    @Bean
    public BookCargoUseCase bookCargoUseCase(CargoRepository cargoes, ShipperRepository shippers,
            LocationRepository locations, Clock clock) {
        return new BookCargoUseCase(cargoes, shippers, locations, clock);
    }

    @Bean
    public SearchCargoUseCase searchCargoUseCase(CargoRepository cargoes) {
        return new SearchCargoUseCase(cargoes);
    }

    @Bean
    public RequestRoutingUseCase requestRoutingUseCase(CargoRepository cargoes) {
        return new RequestRoutingUseCase(cargoes);
    }
}
