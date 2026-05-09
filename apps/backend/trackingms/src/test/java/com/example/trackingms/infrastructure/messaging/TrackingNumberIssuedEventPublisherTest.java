package com.example.trackingms.infrastructure.messaging;

import com.example.trackingms.application.internal.commandservices.TrackingNumberService;
import com.example.trackingms.domain.events.TrackingNumberIssuedEvent;
import com.example.trackingms.domain.ports.TrackingEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers RabbitMQ を使った TrackingNumberIssuedEvent 発行連携テスト
 *
 * <p>RabbitMQ コンテナを起動し、追跡番号発行後にイベントがキューに届くことを検証する。
 */
@SpringBootTest
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@DisplayName("TrackingNumberIssuedEvent RabbitMQ 連携テスト")
class TrackingNumberIssuedEventPublisherTest {

    private static final String TEST_QUEUE = "test.tracking.number.issued";

    @Container
    static final RabbitMQContainer rabbitMq = new RabbitMQContainer("rabbitmq:3.13-management");

    @DynamicPropertySource
    static void registerRabbitMqProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbitMq::getHost);
        registry.add("spring.rabbitmq.port", rabbitMq::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitMq::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMq::getAdminPassword);
    }

    /** テスト用 Bean 設定: TrackingEventPublisher・Exchange・Queue・Binding を明示定義 */
    @TestConfiguration
    static class RabbitTestConfig {

        @Bean
        @Primary
        TrackingEventPublisher testTrackingEventPublisher(RabbitTemplate rabbitTemplate) {
            JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
            rabbitTemplate.setMessageConverter(converter);
            return new RabbitMqTrackingEventPublisher(rabbitTemplate);
        }

        @Bean
        Queue testTrackingNumberIssuedQueue() {
            return new Queue(TEST_QUEUE, false, false, false);
        }

        @Bean
        TopicExchange trackingEventsExchange() {
            return new TopicExchange(RabbitMqTrackingEventPublisher.EXCHANGE, false, false);
        }

        @Bean
        Binding testQueueBinding(Queue testTrackingNumberIssuedQueue, TopicExchange trackingEventsExchange) {
            return BindingBuilder.bind(testTrackingNumberIssuedQueue)
                    .to(trackingEventsExchange)
                    .with(RabbitMqTrackingEventPublisher.ROUTING_KEY_TRACKING_NUMBER_ISSUED);
        }

        @Bean
        RabbitAdmin rabbitAdmin(org.springframework.amqp.rabbit.connection.ConnectionFactory cf) {
            RabbitAdmin admin = new RabbitAdmin(cf);
            admin.setAutoStartup(true);
            return admin;
        }
    }

    @Autowired
    TrackingNumberService trackingNumberService;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    RabbitAdmin rabbitAdmin;

    @BeforeEach
    void setUp() {
        rabbitAdmin.initialize();
    }

    @Test
    @DisplayName("issueTrackingNumber 後に TrackingNumberIssuedEvent がキューに届くこと")
    void shouldPublishTrackingNumberIssuedEventAfterIssuing() throws Exception {
        // 追跡番号を発行する
        var activity = trackingNumberService.issueTrackingNumber("BK-001234");

        // キューからメッセージを受信（最大 5 秒待機）
        var message = rabbitTemplate.receiveAndConvert(TEST_QUEUE, 5000);

        assertThat(message).isNotNull().isInstanceOf(TrackingNumberIssuedEvent.class);

        var event = (TrackingNumberIssuedEvent) message;
        assertThat(event.bookingId()).isEqualTo("BK-001234");
        assertThat(event.trackingNumber()).isEqualTo(activity.getTrackingNumber().number());
    }

    @Test
    @DisplayName("既に発行済みの追跡番号に対してはイベントがキューに届かないこと")
    void shouldNotPublishEventWhenTrackingNumberAlreadyIssued() throws Exception {
        // 1回目: 追跡番号を発行（イベントが届く）
        trackingNumberService.issueTrackingNumber("BK-001235");
        // キューを空にする
        rabbitTemplate.receiveAndConvert(TEST_QUEUE, 2000);

        // 2回目: 同じ bookingId で呼び出し（イベントは届かないはず）
        trackingNumberService.issueTrackingNumber("BK-001235");

        var message = rabbitTemplate.receiveAndConvert(TEST_QUEUE, 2000);
        assertThat(message).isNull();
    }
}
