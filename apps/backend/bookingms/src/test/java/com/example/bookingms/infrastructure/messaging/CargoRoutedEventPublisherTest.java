package com.example.bookingms.infrastructure.messaging;

import com.example.bookingms.application.internal.commandservices.CargoCommandService;
import com.example.bookingms.application.internal.commandservices.RouteCargoCommand;
import com.example.bookingms.application.internal.commandservices.RouteCargoCommand.LegData;
import com.example.bookingms.domain.ports.CargoEventPublisher;
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
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.example.bookingms.domain.events.CargoRoutedEvent;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers RabbitMQ を使った CargoRoutedEvent 発行連携テスト
 *
 * <p>RabbitMQ コンテナを起動し、経路割当後にイベントがキューに届くことを検証する。
 */
@SpringBootTest
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Sql(statements = {"DELETE FROM leg", "DELETE FROM cargo"},
     executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@DisplayName("CargoRoutedEvent RabbitMQ 連携テスト")
class CargoRoutedEventPublisherTest {

    private static final String TEST_QUEUE = "test.cargo.routed";

    @Container
    static final RabbitMQContainer rabbitMq = new RabbitMQContainer("rabbitmq:3.13-management");

    @DynamicPropertySource
    static void registerRabbitMqProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbitMq::getHost);
        registry.add("spring.rabbitmq.port", rabbitMq::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitMq::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMq::getAdminPassword);
    }

    /** テスト用 Bean 設定: CargoEventPublisher・Exchange・Queue・Binding を明示定義 */
    @TestConfiguration
    static class RabbitTestConfig {

        @Bean
        @Primary
        CargoEventPublisher testCargoEventPublisher(RabbitTemplate rabbitTemplate) {
            JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
            rabbitTemplate.setMessageConverter(converter);
            return new RabbitMqCargoEventPublisher(rabbitTemplate);
        }

        @Bean
        Queue testCargoRoutedQueue() {
            return new Queue(TEST_QUEUE, false, false, false);
        }

        @Bean
        TopicExchange cargoEventsExchange() {
            return new TopicExchange(RabbitMqCargoEventPublisher.EXCHANGE, false, false);
        }

        @Bean
        Binding testQueueBinding(Queue testCargoRoutedQueue, TopicExchange cargoEventsExchange) {
            return BindingBuilder.bind(testCargoRoutedQueue)
                    .to(cargoEventsExchange)
                    .with(RabbitMqCargoEventPublisher.ROUTING_KEY_CARGO_ROUTED);
        }

        @Bean
        RabbitAdmin rabbitAdmin(org.springframework.amqp.rabbit.connection.ConnectionFactory cf) {
            RabbitAdmin admin = new RabbitAdmin(cf);
            admin.setAutoStartup(true);
            return admin;
        }

        @Bean
        Queue trackingNumberIssuedQueue() {
            return new Queue(TrackingNumberIssuedEventListener.QUEUE, false, false, false);
        }

        @Bean
        TopicExchange trackingEventsExchange() {
            return new TopicExchange("tracking.events", false, false);
        }

        @Bean
        Binding trackingNumberIssuedBinding(Queue trackingNumberIssuedQueue,
                                             TopicExchange trackingEventsExchange) {
            return BindingBuilder.bind(trackingNumberIssuedQueue)
                    .to(trackingEventsExchange)
                    .with("tracking.number.issued");
        }
    }

    @Autowired
    CargoCommandService cargoCommandService;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    RabbitAdmin rabbitAdmin;

    @BeforeEach
    void setUp() {
        rabbitAdmin.initialize();
    }

    @Test
    @DisplayName("assignRoute 後に CargoRoutedEvent がキューに届くこと")
    void shouldPublishCargoRoutedEventAfterAssigningRoute() throws Exception {
        // 1. 貨物を登録
        var cargo = cargoCommandService.registerBooking(new CargoCommandService.RegisterBookingCommand(
                1L, "GENERAL", BigDecimal.valueOf(100),
                "JPTYO", "CNSHA", LocalDate.of(2026, 6, 30), null, null));

        // 2. 経路を割り当てる
        var command = new RouteCargoCommand(List.of(new LegData(
                "V0042", "JPTYO", "CNSHA",
                LocalDateTime.of(2026, 4, 1, 18, 0),
                LocalDateTime.of(2026, 4, 14, 8, 0))));
        cargoCommandService.assignRoute(cargo.getBookingId().getId(), command);

        // 3. キューからメッセージを受信（最大 5 秒待機）
        var message = rabbitTemplate.receiveAndConvert(TEST_QUEUE, 5000);

        assertThat(message).isNotNull().isInstanceOf(CargoRoutedEvent.class);

        var event = (CargoRoutedEvent) message;
        assertThat(event.bookingId()).isEqualTo(cargo.getBookingId().getId());
        assertThat(event.originUnlocode()).isEqualTo("JPTYO");
        assertThat(event.destinationUnlocode()).isEqualTo("CNSHA");
    }
}
