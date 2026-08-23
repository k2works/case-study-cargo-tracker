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

    @Bean
    public TrackingActivityRepository trackingActivityRepository(TrackingActivityMapper mapper) {
        return new MyBatisTrackingActivityRepository(mapper);
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
    public AdvanceTrackingUseCase advanceTrackingUseCase(TrackingActivityRepository activities) {
        return new AdvanceTrackingUseCase(activities);
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
