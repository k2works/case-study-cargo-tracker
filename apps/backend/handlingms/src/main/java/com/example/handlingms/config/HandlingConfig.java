package com.example.handlingms.config;

import com.example.handlingms.application.internal.RegisterHandlingActivityUseCase;
import com.example.handlingms.application.port.CargoSnapshotFinder;
import com.example.handlingms.application.internal.ManageCustomsDeclarationUseCase;
import com.example.handlingms.application.internal.RegisterCustomsDeclarationUseCase;
import com.example.handlingms.application.port.CustomsDeclarationRepository;
import com.example.handlingms.application.port.HandlingActivityRepository;
import com.example.handlingms.application.port.LocationRepository;
import com.example.handlingms.application.port.HandlingEventNotifier;
import com.example.handlingms.infrastructure.messaging.HandlingEventChannels;
import com.example.handlingms.infrastructure.messaging.RabbitHandlingEventNotifier;
import com.example.handlingms.infrastructure.persistence.CustomsDeclarationMapper;
import com.example.handlingms.infrastructure.persistence.CustomsStatusHistoryMapper;
import com.example.handlingms.infrastructure.persistence.HandlingActivityMapper;
import com.example.handlingms.infrastructure.persistence.MyBatisCustomsDeclarationRepository;
import com.example.handlingms.infrastructure.persistence.LocationMapper;
import com.example.handlingms.infrastructure.persistence.MyBatisHandlingActivityRepository;
import com.example.handlingms.infrastructure.persistence.MyBatisLocationRepository;
import com.example.handlingms.infrastructure.booking.RestCargoSnapshotFinder;
import com.example.shared.auth.AuthenticatedUserFilter;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
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

    /**
     * イベントを JSON で送る。
     *
     * <p>既定の Java 直列化にすると、受け手が同じクラスを持っていることが前提になり、
     * サービスの独立性が消える（[ADR-022] 決定 3 の「知らない項目を無視する」も成り立たない）。
     */
    @Bean
    public MessageConverter handlingEventMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    /**
     * 荷役のイベントを流す先（[ADR-023] 決定 5）。
     *
     * <p><strong>行き場のないイベントを予備の交換機へ逃がす</strong>（[ADR-022] 決定 4）。
     * ルーティングキーの綴り違いや購読側の配線漏れでは、イベントはどのキューにも入らず
     * 黙って消え、発行側は成功を返す。
     *
     * <p>購読側（trackingms）と同じ内容で宣言する。引数が食い違うと、後から接続した
     * ほうが PRECONDITION_FAILED で落ちる。
     */
    @Bean
    public TopicExchange cargoHandlingExchange() {
        return new TopicExchange(HandlingEventChannels.EXCHANGE, true, false,
                Map.of("alternate-exchange", HandlingEventChannels.UNROUTABLE_EXCHANGE));
    }

    /**
     * 行き場のないイベントの受け皿。
     *
     * <p>宣言は冪等なので、発行側と購読側の両方が宣言してよい。片方だけに置くと、
     * そのサービスが起動していない環境で受け皿が消える。
     */
    @Bean
    public FanoutExchange handlingUnroutableExchange() {
        return new FanoutExchange(HandlingEventChannels.UNROUTABLE_EXCHANGE, true, false);
    }

    @Bean
    public Queue handlingUnroutableQueue() {
        return new Queue(HandlingEventChannels.UNROUTABLE_QUEUE, true);
    }

    @Bean
    public Binding handlingUnroutableBinding() {
        return BindingBuilder.bind(handlingUnroutableQueue()).to(handlingUnroutableExchange());
    }

    @Bean
    public HandlingEventNotifier handlingEventNotifier(RabbitTemplate rabbitTemplate) {
        return new RabbitHandlingEventNotifier(rabbitTemplate);
    }

    @Bean
    public HandlingActivityRepository handlingActivityRepository(HandlingActivityMapper mapper) {
        return new MyBatisHandlingActivityRepository(mapper);
    }

    @Bean
    public LocationRepository locationRepository(LocationMapper mapper) {
        return new MyBatisLocationRepository(mapper);
    }

    @Bean
    public RegisterCustomsDeclarationUseCase registerCustomsDeclarationUseCase(
            CustomsDeclarationRepository declarations, CargoSnapshotFinder cargoes) {
        return new RegisterCustomsDeclarationUseCase(declarations, cargoes);
    }

    @Bean
    public ManageCustomsDeclarationUseCase manageCustomsDeclarationUseCase(
            CustomsDeclarationRepository declarations, Clock clock) {
        return new ManageCustomsDeclarationUseCase(declarations, clock);
    }

    @Bean
    public CustomsDeclarationRepository customsDeclarationRepository(
            CustomsDeclarationMapper declarations, CustomsStatusHistoryMapper histories) {
        return new MyBatisCustomsDeclarationRepository(declarations, histories);
    }

    /**
     * 時刻源は業務タイムゾーンで持つ（[ADR-010]）。
     *
     * <p>UTC で「いま」を決めると、時差の分だけ日付がずれる時間帯ができる。日中しか
     * 動かさないと気づかない。
     */
    @Bean
    public Clock businessClock(@Value("${app.business-time-zone}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }

    @Bean
    public RegisterHandlingActivityUseCase registerHandlingActivityUseCase(
            CargoSnapshotFinder cargoes, LocationRepository locations,
            HandlingActivityRepository activities, CustomsDeclarationRepository declarations,
            HandlingEventNotifier notifier, Clock clock) {
        return new RegisterHandlingActivityUseCase(cargoes, locations, activities, declarations,
                notifier, clock);
    }
}
