package com.example.trackingms.config;

import com.example.shared.auth.AuthenticatedUserFilter;
import com.example.trackingms.application.internal.AdvanceTrackingUseCase;
import com.example.trackingms.application.internal.StartTrackingUseCase;
import com.example.trackingms.application.port.LocationRepository;
import com.example.trackingms.application.port.TrackingActivityRepository;
import com.example.trackingms.infrastructure.messaging.HandlingActivityRegisteredListener;
import com.example.trackingms.infrastructure.messaging.TrackingEventChannels;
import com.example.trackingms.infrastructure.messaging.TrackingNumberIssuedListener;
import com.example.trackingms.infrastructure.persistence.LocationMapper;
import com.example.trackingms.infrastructure.persistence.MyBatisLocationRepository;
import com.example.trackingms.infrastructure.persistence.MyBatisTrackingActivityRepository;
import com.example.trackingms.infrastructure.persistence.TrackingActivityMapper;
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

@Configuration
public class TrackingConfig {

    /**
     * 公開する接頭辞（US18-5・[ADR-024] 決定 5）。
     *
     * <p><strong>1 本だけである。</strong>接頭辞で分けているので、ここに足すことは
     * 「新しい公開経路を作る」と同義になる。足すときは決定 5 を読み直す。
     */
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

    /**
     * 公開の追跡照会に上限を置く（[ADR-024] 決定 6）。
     *
     * <p>認証が無い唯一の業務経路であり、追跡番号は日付が既知なら 4 桁しかない。
     *
     * <p><strong>ヘルスチェックには掛からない。</strong>フィルタが接頭辞で絞っている
     * ——一律に掛けると、過負荷のときに liveness が 429 を返して再起動ループになる。
     */
    @Bean
    public FilterRegistrationBean<com.example.trackingms.interfaces.rest
            .PublicLookupThrottleFilter> publicLookupThrottleFilter(
            @org.springframework.beans.factory.annotation.Value(
                    "${app.public-lookup.limit-per-minute:30}") int limitPerMinute,
            java.time.Clock clock) {
        FilterRegistrationBean<com.example.trackingms.interfaces.rest.PublicLookupThrottleFilter>
                registration = new FilterRegistrationBean<>(
                        new com.example.trackingms.interfaces.rest.PublicLookupThrottleFilter(
                                PUBLIC_PATH_PREFIX, limitPerMinute, clock));
        // 認証フィルタの直後に置く。公開経路は認証を通らないので、順序は実質ここが先頭になる
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    @Bean
    public TrackingActivityRepository trackingActivityRepository(TrackingActivityMapper mapper,
            com.example.trackingms.infrastructure.persistence.TrackingEventMapper events,
            com.example.trackingms.infrastructure.persistence.TrackingExceptionMapper exceptions) {
        return new MyBatisTrackingActivityRepository(mapper, events, exceptions);
    }

    @Bean
    public LocationRepository locationRepository(LocationMapper mapper) {
        return new MyBatisLocationRepository(mapper);
    }

    @Bean
    public StartTrackingUseCase startTrackingUseCase(TrackingActivityRepository activities,
            LocationRepository locations) {
        return new StartTrackingUseCase(activities, locations);
    }

    @Bean
    public AdvanceTrackingUseCase advanceTrackingUseCase(TrackingActivityRepository activities,
            LocationRepository locations,
            com.example.trackingms.application.port.TrackingNotifier notifier) {
        return new AdvanceTrackingUseCase(activities, locations, notifier);
    }

    /**
     * 時刻源は業務タイムゾーンで持つ（[ADR-010]）。
     *
     * <p>UTC で「いま」を決めると、時差の分だけ日付がずれる時間帯ができる。日中しか
     * 動かさないと気づかない。
     */
    @Bean
    public java.time.Clock businessClock(
            @org.springframework.beans.factory.annotation.Value("${app.business-time-zone}")
            String zone) {
        return java.time.Clock.system(java.time.ZoneId.of(zone));
    }

    @Bean
    public com.example.trackingms.application.port.TrackingNoticeRepository trackingNoticeRepository(
            com.example.trackingms.infrastructure.persistence.TrackingNoticeMapper mapper) {
        return new com.example.trackingms.infrastructure.persistence
                .MyBatisTrackingNoticeRepository(mapper);
    }

    @Bean
    public com.example.trackingms.application.port.TrackingLookupLogger trackingLookupLogger(
            com.example.trackingms.infrastructure.persistence.TrackingLookupLogMapper mapper) {
        return new com.example.trackingms.infrastructure.persistence
                .MyBatisTrackingLookupLogger(mapper);
    }

    /**
     * 荷主への通知は<strong>記録で代替する</strong>（[ADR-024] 決定 9）。
     *
     * <p>メール送信を実装する日は、ここを差し替える。業務のコードは動かない。
     */
    @Bean
    public com.example.trackingms.application.port.TrackingNotifier trackingNotifier(
            com.example.trackingms.application.port.TrackingNoticeRepository notices,
            java.time.Clock clock) {
        return new com.example.trackingms.infrastructure.notification
                .RecordingTrackingNotifier(notices, clock);
    }

    @Bean
    public com.example.trackingms.application.internal.TrackingLookupUseCase trackingLookupUseCase(
            TrackingActivityRepository activities,
            com.example.trackingms.application.port.TrackingLookupLogger lookupLogger) {
        return new com.example.trackingms.application.internal.TrackingLookupUseCase(
                activities, lookupLogger);
    }

    @Bean
    public com.example.trackingms.application.internal.ManageTrackingUseCase manageTrackingUseCase(
            TrackingActivityRepository activities, LocationRepository locations,
            com.example.trackingms.application.port.TrackingNotifier notifier,
            java.time.Clock clock) {
        return new com.example.trackingms.application.internal.ManageTrackingUseCase(
                activities, locations, notifier, clock);
    }

    @Bean
    public HandlingActivityRegisteredListener handlingActivityRegisteredListener(
            AdvanceTrackingUseCase advanceTracking) {
        return new HandlingActivityRegisteredListener(advanceTracking);
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
     * 荷役のイベントを受け取るキュー。
     *
     * <p><strong>受け取れなかったイベントの行き先を、キューの宣言と同じ場所で決める</strong>
     * （[ADR-022] 決定 4）。別々に置くと、キューだけ作ってデッドレターを忘れた環境ができる。
     */
    @Bean
    public Queue handlingActivityRegisteredQueue() {
        return new Queue(TrackingEventChannels.HANDLING_QUEUE, true, false, false, Map.of(
                "x-dead-letter-exchange", TrackingEventChannels.DEAD_LETTER_EXCHANGE,
                "x-dead-letter-routing-key", TrackingEventChannels.HANDLING_DEAD_LETTER_QUEUE));
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

    @Bean
    public TrackingNumberIssuedListener trackingNumberIssuedListener(
            StartTrackingUseCase startTracking) {
        return new TrackingNumberIssuedListener(startTracking);
    }

    @Bean
    public com.example.trackingms.application.internal.ApplyEstimatedArrivalUseCase
            applyEstimatedArrivalUseCase(TrackingActivityRepository activities) {
        return new com.example.trackingms.application.internal.ApplyEstimatedArrivalUseCase(
                activities);
    }

    @Bean
    public com.example.trackingms.infrastructure.messaging.CargoRoutedListener cargoRoutedListener(
            com.example.trackingms.application.internal.ApplyEstimatedArrivalUseCase
                    applyEstimatedArrival) {
        return new com.example.trackingms.infrastructure.messaging.CargoRoutedListener(
                applyEstimatedArrival);
    }

    /**
     * 経路のイベントを読むキュー（[ADR-024] 決定 4）。
     *
     * <p><strong>受け取れなかったイベントの行き先を、キューの宣言と同じ場所で決める</strong>
     * （[ADR-022] 決定 4）。別々に置くと、キューだけ作ってデッドレターを忘れた環境ができる。
     */
    @Bean
    public Queue cargoRoutedQueue() {
        return new Queue(TrackingEventChannels.CARGO_ROUTED_QUEUE, true, false, false, Map.of(
                "x-dead-letter-exchange", TrackingEventChannels.DEAD_LETTER_EXCHANGE,
                "x-dead-letter-routing-key",
                TrackingEventChannels.CARGO_ROUTED_DEAD_LETTER_QUEUE));
    }

    @Bean
    public Binding cargoRoutedBinding() {
        return BindingBuilder.bind(cargoRoutedQueue()).to(cargoEventExchange())
                .with(TrackingEventChannels.CARGO_ROUTED);
    }

    @Bean
    public Queue cargoRoutedDeadLetterQueue() {
        return new Queue(TrackingEventChannels.CARGO_ROUTED_DEAD_LETTER_QUEUE, true);
    }

    @Bean
    public Binding cargoRoutedDeadLetterBinding() {
        return BindingBuilder.bind(cargoRoutedDeadLetterQueue()).to(trackingDeadLetterExchange())
                .with(TrackingEventChannels.CARGO_ROUTED_DEAD_LETTER_QUEUE);
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
        return new Queue(TrackingEventChannels.QUEUE, true, false, false, Map.of(
                "x-dead-letter-exchange", TrackingEventChannels.DEAD_LETTER_EXCHANGE,
                "x-dead-letter-routing-key", TrackingEventChannels.DEAD_LETTER_QUEUE));
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
}
