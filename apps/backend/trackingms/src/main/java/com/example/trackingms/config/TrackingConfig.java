package com.example.trackingms.config;

import com.example.shared.auth.AuthenticatedUserFilter;
import com.example.trackingms.application.internal.StartTrackingUseCase;
import com.example.trackingms.application.port.LocationRepository;
import com.example.trackingms.application.port.TrackingActivityRepository;
import com.example.trackingms.infrastructure.messaging.TrackingEventChannels;
import com.example.trackingms.infrastructure.messaging.TrackingNumberIssuedListener;
import com.example.trackingms.infrastructure.persistence.LocationMapper;
import com.example.trackingms.infrastructure.persistence.MyBatisLocationRepository;
import com.example.trackingms.infrastructure.persistence.MyBatisTrackingActivityRepository;
import com.example.trackingms.infrastructure.persistence.TrackingActivityMapper;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
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

    @Bean
    public TopicExchange cargoEventExchange() {
        return new TopicExchange(TrackingEventChannels.EXCHANGE, true, false);
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
}
