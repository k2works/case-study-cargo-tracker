package com.example.trackingms;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.trackingms.application.port.TrackingActivityRepository;
import com.example.trackingms.domain.model.TrackingNumber;
import com.example.trackingms.domain.model.TransportStatus;
import com.example.trackingms.infrastructure.messaging.TrackingEventChannels;
import com.example.trackingms.infrastructure.messaging.TrackingNumberIssuedMessage;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * イベントが<strong>実際に届く</strong>ことを、実 RabbitMQ で確かめる（成功基準 2・3）。
 *
 * <p>「発行するコードを書いた」「購読するコードを書いた」ことと、<strong>相手に届くこと</strong>は
 * 別である。交換機の名前・ルーティングキー・キューの結びつけ・変換器のどれか 1 つがずれると、
 * <strong>送り手はエラーにならないまま届かない</strong>。IT5 では同じ形の食い違い（接続先の設定）が
 * 実環境まで誰にも見えなかった。
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("integration")
@DisplayName("イベントの往復（実 RabbitMQ）")
class TrackingEventRoundTripTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3-management-alpine");

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private RabbitListenerEndpointRegistry listeners;

    @Autowired
    private TrackingActivityRepository activities;

    private void startListening() {
        if (!listeners.isRunning()) {
            listeners.start();
        }
    }

    private static TrackingNumberIssuedMessage message(String trackingNumber) {
        return new TrackingNumberIssuedMessage(trackingNumber, "BKG-2026000001",
                "JPTYO", "USLAX", LocalDate.of(2030, Month.SEPTEMBER, 20),
                Instant.parse("2026-08-22T02:00:00Z"));
    }

    private void publish(Object payload) {
        rabbitTemplate.convertAndSend(TrackingEventChannels.EXCHANGE,
                TrackingEventChannels.TRACKING_NUMBER_ISSUED, payload);
    }

    /** 成功基準 2。 */
    @Test
    @DisplayName("発行されたイベントが届き、追跡の記録が残る")
    void startsTrackingWhenTheEventArrives() {
        startListening();
        String number = "TRK-20260822-9001";

        publish(message(number));

        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(activities.findByTrackingNumber(TrackingNumber.of(number)))
                        .as("イベントは送ったのに追跡が作られていない")
                        .isPresent());

        // 地点はこちらのマスタから引く（イベントが運ぶのは UN/LOCODE だけ）
        assertThat(activities.findByTrackingNumber(TrackingNumber.of(number)).orElseThrow())
                .satisfies(activity -> {
                    assertThat(activity.origin().name()).isEqualTo("Tokyo");
                    assertThat(activity.destination().name()).isEqualTo("Los Angeles");
                    assertThat(activity.transportStatus()).isEqualTo(TransportStatus.NOT_RECEIVED);
                    assertThat(activity.bookingId().value()).isEqualTo("BKG-2026000001");
                });
    }

    /** [ADR-022] 決定 5。再試行がある以上、二重配送は起こる。 */
    @Test
    @DisplayName("同じイベントが 2 回届いても追跡は 1 件")
    void isIdempotent() {
        startListening();
        String number = "TRK-20260822-9002";
        // 他のテストが残したぶんと混ざらないよう、増分で見る（実行順に依存させない）
        long deadLettersBefore = deadLetterCount();

        publish(message(number));
        publish(message(number));

        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(activities.findByTrackingNumber(TrackingNumber.of(number))).isPresent());
        // 2 件目で一意制約に当たって落ちていれば、デッドレターに溜まる
        Awaitility.await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(deadLetterCount())
                        .as("同じイベントの 2 回目で落ちている。冪等になっていない")
                        .isEqualTo(deadLettersBefore));
    }

    /**
     * 成功基準 3。<strong>受け取れなかったイベントが消えない</strong>。
     *
     * <p>設定を書いたことと、落ちたイベントがそこへ届くことは別である。処理できない中身を
     * 送って、実際にデッドレターへ回ることを見る。
     */
    @Test
    @DisplayName("処理できなかったイベントはデッドレターに残る")
    void movesUnprocessableEventsToTheDeadLetterQueue() {
        startListening();
        long before = deadLetterCount();

        // 地点マスタに無い港。握りつぶすと、出発地の分からない追跡ができる
        publish(new TrackingNumberIssuedMessage("TRK-20260822-9003", "BKG-2026000002",
                "XXXXX", "USLAX", LocalDate.of(2030, Month.SEPTEMBER, 20),
                Instant.parse("2026-08-22T02:00:00Z")));

        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(deadLetterCount())
                        .as("処理できなかったイベントがどこにも残っていない")
                        .isGreaterThan(before));

        assertThat(activities.findByTrackingNumber(TrackingNumber.of("TRK-20260822-9003")))
                .as("処理できなかったのに追跡を作っている")
                .isEmpty();
    }

    private long deadLetterCount() {
        var info = rabbitAdmin.getQueueInfo(TrackingEventChannels.DEAD_LETTER_QUEUE);
        return info == null ? 0L : info.getMessageCount();
    }
}
