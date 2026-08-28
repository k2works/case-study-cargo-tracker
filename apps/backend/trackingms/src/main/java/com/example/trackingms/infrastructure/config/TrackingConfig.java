package com.example.trackingms.infrastructure.config;

import com.example.shared.auth.AuthenticatedUserFilter;
import com.example.trackingms.domain.repository.LocationRepository;
import com.example.trackingms.application.internal.outboundservices.acl.ShipperCargoSnapshotFinder;
import com.example.trackingms.domain.repository.TrackingActivityRepository;
import com.example.trackingms.domain.repository.TrackingLookupLogger;
import com.example.trackingms.domain.repository.TrackingNoticeRepository;
import com.example.trackingms.application.internal.outboundservices.acl.TrackingNotifier;
import com.example.trackingms.application.internal.outboundservices.acl.UserShipperLinkFinder;
import com.example.trackingms.infrastructure.acl.RestShipperCargoSnapshotFinder;
import com.example.trackingms.infrastructure.acl.RestUserShipperLinkFinder;
import com.example.trackingms.interfaces.events.TrackingEventChannels;
import com.example.trackingms.infrastructure.acl.RecordingTrackingNotifier;
import com.example.trackingms.infrastructure.repositories.LocationMapper;
import com.example.trackingms.infrastructure.repositories.MyBatisLocationRepository;
import com.example.trackingms.infrastructure.repositories.MyBatisTrackingActivityRepository;
import com.example.trackingms.infrastructure.repositories.MyBatisTrackingLookupLogger;
import com.example.trackingms.infrastructure.repositories.MyBatisTrackingNoticeRepository;
import com.example.trackingms.infrastructure.repositories.TrackingActivityMapper;
import com.example.trackingms.infrastructure.repositories.TrackingEventMapper;
import com.example.trackingms.infrastructure.repositories.TrackingExceptionMapper;
import com.example.trackingms.infrastructure.repositories.TrackingLookupLogMapper;
import com.example.trackingms.infrastructure.repositories.TrackingNoticeMapper;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class TrackingConfig {

    /**
     * 公開する接頭辞（US18-5・[ADR-024] 決定 5）。
     *
     * <p><strong>1 本だけである。</strong>接頭辞で分けているので、ここに足すことは
     * 「新しい公開経路を作る」と同義になる。足すときは決定 5 を読み直す。
     */
    @SuppressWarnings("java:S1075") // 業務の経路であり、環境で変わる URI ではない
    public static final String PUBLIC_PATH_PREFIX = "/api/v1/public/";

    /**
     * Gateway が付けた利用者ヘッダを必須とする（ADR-007）。
     *
     * <p>認可を書き忘れたエンドポイントが無認証で開くことを、認可判定より前で塞ぐ。
     *
     * <p>除外はヘルスチェックと<strong>公開の追跡照会</strong>だけである（US18-5）。
     * 荷主はログインしないため、この 1 本は認証の外に置く。
     */
    @Bean
    public FilterRegistrationBean<AuthenticatedUserFilter> authenticatedUserFilter() {
        FilterRegistrationBean<AuthenticatedUserFilter> registration =
                new FilterRegistrationBean<>(
                        new AuthenticatedUserFilter(java.util.List.of(PUBLIC_PATH_PREFIX)));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public TrackingActivityRepository trackingActivityRepository(TrackingActivityMapper mapper,
            TrackingEventMapper events,
            TrackingExceptionMapper exceptions) {
        return new MyBatisTrackingActivityRepository(mapper, events, exceptions);
    }

    @Bean
    public LocationRepository locationRepository(LocationMapper mapper) {
        return new MyBatisLocationRepository(mapper);
    }

    /**
     * 時刻源は業務タイムゾーンで持つ（[ADR-010]）。
     *
     * <p>UTC で「いま」を決めると、時差の分だけ日付がずれる時間帯ができる。日中しか
     * 動かさないと気づかない。
     */
    @Bean
    public ZoneId businessZone(
            @org.springframework.beans.factory.annotation.Value("${app.business-time-zone}")
            String zone) {
        return ZoneId.of(zone);
    }

    @Bean
    public Clock businessClock(ZoneId businessZone) {
        return Clock.system(businessZone);
    }

    @Bean
    public TrackingNoticeRepository trackingNoticeRepository(
            TrackingNoticeMapper mapper) {
        return new MyBatisTrackingNoticeRepository(mapper);
    }

    @Bean
    public TrackingLookupLogger trackingLookupLogger(
            TrackingLookupLogMapper mapper) {
        return new MyBatisTrackingLookupLogger(mapper);
    }

    /**
     * 荷主への通知は<strong>記録で代替する</strong>（[ADR-024] 決定 9）。
     *
     * <p>メール送信を実装する日は、ここを差し替える。業務のコードは動かない。
     */
    @Bean
    public TrackingNotifier trackingNotifier(
            TrackingNoticeRepository notices,
            Clock clock) {
        return new RecordingTrackingNotifier(notices, clock);
    }

    @Bean
    public UserShipperLinkFinder userShipperLinkFinder(
            @org.springframework.beans.factory.annotation.Value("${app.auth-service.base-url}")
            String baseUrl) {
        return new RestUserShipperLinkFinder(RestClient.builder().baseUrl(baseUrl)
                .requestFactory(internalRequestFactory()).build());
    }

    @Bean
    public ShipperCargoSnapshotFinder shipperCargoSnapshotFinder(
            @org.springframework.beans.factory.annotation.Value("${app.booking-service.base-url}")
            String baseUrl) {
        return new RestShipperCargoSnapshotFinder(RestClient.builder().baseUrl(baseUrl)
                .requestFactory(internalRequestFactory()).build());
    }

    private static ClientHttpRequestFactory internalRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(2));
        factory.setReadTimeout(java.time.Duration.ofSeconds(5));
        return factory;
    }

    /**
     * 荷役の交換機（[ADR-023] 決定 5）。
     *
     * <p>予約の交換機とは分ける。相乗りすると、購読側のキューの結びつけが増えるたびに
     * 無関係なイベントまで配られる。
     *
     * <p>発行側（handlingms）と同じ内容で宣言する。引数が食い違うと、後から接続した
     * ほうが PRECONDITION_FAILED で落ちる。
     */
    @Bean
    public TopicExchange cargoHandlingExchange() {
        return new TopicExchange(TrackingEventChannels.HANDLING_EXCHANGE, true, false,
                Map.of("alternate-exchange", TrackingEventChannels.UNROUTABLE_EXCHANGE));
    }

    /**
     * キャンセルのイベントを受け取るキュー（[ADR-025] 決定 3）。
     *
     * <p><strong>購読側ごとにキューを分ける。</strong>共有すると、片方が読んだイベントを
     * もう片方が受け取れない。billingms が購読する日（US21・IT11）は、キューと
     * 結びつけを足すだけで済む。
     */
    @Bean
    public Queue cargoCancelledQueue() {
        return subscriberQueue(TrackingEventChannels.CANCELLED_QUEUE,
                TrackingEventChannels.CANCELLED_DEAD_LETTER_QUEUE);
    }

    @Bean
    public Queue cargoCancelledDeadLetterQueue() {
        return new Queue(TrackingEventChannels.CANCELLED_DEAD_LETTER_QUEUE, true);
    }

    @Bean
    public Binding cargoCancelledDeadLetterBinding() {
        return BindingBuilder.bind(cargoCancelledDeadLetterQueue())
                .to(trackingDeadLetterExchange())
                .with(TrackingEventChannels.CANCELLED_DEAD_LETTER_QUEUE);
    }

    @Bean
    public Binding cargoCancelledBinding() {
        return BindingBuilder.bind(cargoCancelledQueue())
                .to(cargoEventExchange())
                .with(TrackingEventChannels.CARGO_CANCELLED);
    }

    /** 通関のイベントを受け取るキュー（US29-5）。**購読側ごとに分ける**。 */
    @Bean
    public Queue customsStatusChangedQueue() {
        return subscriberQueue(TrackingEventChannels.CUSTOMS_QUEUE,
                TrackingEventChannels.CUSTOMS_DEAD_LETTER_QUEUE);
    }

    @Bean
    public Queue customsStatusChangedDeadLetterQueue() {
        return new Queue(TrackingEventChannels.CUSTOMS_DEAD_LETTER_QUEUE, true);
    }

    @Bean
    public Binding customsStatusChangedDeadLetterBinding() {
        return BindingBuilder.bind(customsStatusChangedDeadLetterQueue())
                .to(trackingDeadLetterExchange())
                .with(TrackingEventChannels.CUSTOMS_DEAD_LETTER_QUEUE);
    }

    @Bean
    public Binding customsStatusChangedBinding() {
        return BindingBuilder.bind(customsStatusChangedQueue())
                .to(cargoHandlingExchange())
                .with(TrackingEventChannels.CUSTOMS_STATUS_CHANGED);
    }

    /**
     * 荷役のイベントを受け取るキュー。
     *
     * <p><strong>受け取れなかったイベントの行き先を、キューの宣言と同じ場所で決める</strong>
     * （[ADR-022] 決定 4）。別々に置くと、キューだけ作ってデッドレターを忘れた環境ができる。
     */
    @Bean
    public Queue handlingActivityRegisteredQueue() {
        return subscriberQueue(TrackingEventChannels.HANDLING_QUEUE,
                TrackingEventChannels.HANDLING_DEAD_LETTER_QUEUE);
    }

    @Bean
    public Binding handlingActivityRegisteredBinding() {
        return BindingBuilder.bind(handlingActivityRegisteredQueue()).to(cargoHandlingExchange())
                .with(TrackingEventChannels.HANDLING_ACTIVITY_REGISTERED);
    }

    @Bean
    public Queue handlingDeadLetterQueue() {
        return new Queue(TrackingEventChannels.HANDLING_DEAD_LETTER_QUEUE, true);
    }

    @Bean
    public Binding handlingDeadLetterBinding() {
        return BindingBuilder.bind(handlingDeadLetterQueue()).to(trackingDeadLetterExchange())
                .with(TrackingEventChannels.HANDLING_DEAD_LETTER_QUEUE);
    }

    /**
     * イベントを JSON で読む。
     *
     * <p>既定の Java 直列化にすると、送り手と同じクラスを持っていることが前提になり、
     * サービスの独立性が消える（[ADR-022] 決定 3 の「知らない項目を無視する」も成り立たない）。
     */
    @Bean
    public MessageConverter trackingEventMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    /**
     * 貨物イベントの交換機。
     *
     * <p><strong>行き場のないイベントを予備の交換機へ逃がす</strong>（[ADR-022] 決定 4）。
     * ルーティングキーの綴り違いや購読側の配線漏れでは、イベントはどのキューにも入らず
     * 黙って消え、発行側は成功を返す。デッドレターはこの形を守らない。
     */
    @Bean
    public TopicExchange cargoEventExchange() {
        return new TopicExchange(TrackingEventChannels.EXCHANGE, true, false,
                Map.of("alternate-exchange", TrackingEventChannels.UNROUTABLE_EXCHANGE));
    }

    /**
     * 受け取るキュー。
     *
     * <p><strong>受け取れなかったイベントの行き先を、キューの宣言と同じ場所で決める</strong>
     * （[ADR-022] 決定 4）。別々に置くと、キューだけ作ってデッドレターを忘れた環境ができ、
     * そこでは落ちたイベントが黙って消える。
     */
    @Bean
    public Queue trackingNumberIssuedQueue() {
        return subscriberQueue(TrackingEventChannels.QUEUE,
                TrackingEventChannels.DEAD_LETTER_QUEUE);
    }

    @Bean
    public Binding trackingNumberIssuedBinding() {
        return BindingBuilder.bind(trackingNumberIssuedQueue()).to(cargoEventExchange())
                .with(TrackingEventChannels.TRACKING_NUMBER_ISSUED);
    }

    @Bean
    public TopicExchange trackingDeadLetterExchange() {
        return new TopicExchange(TrackingEventChannels.DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue trackingDeadLetterQueue() {
        return new Queue(TrackingEventChannels.DEAD_LETTER_QUEUE, true);
    }

    @Bean
    public Binding trackingDeadLetterBinding() {
        return BindingBuilder.bind(trackingDeadLetterQueue()).to(trackingDeadLetterExchange())
                .with(TrackingEventChannels.DEAD_LETTER_QUEUE);
    }

    /**
     * 行き場のないイベントの受け皿（[ADR-022] 決定 4）。
     *
     * <p><strong>発行側と購読側の両方が同じ内容で宣言する。</strong>交換機の引数が食い違うと、
     * 後から接続したほうが PRECONDITION_FAILED で落ちる。宣言は冪等なので、両方が同じものを
     * 宣言しても構わない——片方だけに置くと、そのサービスが起動していない環境で受け皿が
     * 消える。
     */
    @Bean
    public FanoutExchange trackingUnroutableExchange() {
        return new FanoutExchange(TrackingEventChannels.UNROUTABLE_EXCHANGE, true, false);
    }

    @Bean
    public Queue trackingUnroutableQueue() {
        return new Queue(TrackingEventChannels.UNROUTABLE_QUEUE, true);
    }

    @Bean
    public Binding trackingUnroutableBinding() {
        return BindingBuilder.bind(trackingUnroutableQueue()).to(trackingUnroutableExchange());
    }

    /**
     * 購読キューを、<strong>同じ引数で</strong>宣言する。
     *
     * <p>引数の組を 1 か所に集める。キューの引数は<strong>既存の環境では宣言し直せず</strong>、
     * 1 つでも食い違うと {@code PRECONDITION_FAILED} で落ちて、そのサービスは起動しない。
     * 書き写す形にすると、キューが増えるたびに写し間違いの機会が増える。
     */
    private static Queue subscriberQueue(String name, String deadLetterQueue) {
        return new Queue(name, true, false, false, Map.of(
                "x-dead-letter-exchange", TrackingEventChannels.DEAD_LETTER_EXCHANGE,
                "x-dead-letter-routing-key", deadLetterQueue));
    }

}
