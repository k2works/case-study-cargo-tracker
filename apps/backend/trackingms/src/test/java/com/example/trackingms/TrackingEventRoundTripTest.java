package com.example.trackingms;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.trackingms.application.port.TrackingActivityRepository;
import com.example.trackingms.domain.model.TrackingNumber;
import com.example.trackingms.domain.model.TransportStatus;
import com.example.trackingms.infrastructure.messaging.TrackingEventChannels;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Month;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
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

    /**
     * <strong>プロデューサが実際に送る形で流す。</strong>
     *
     * <p>こちらの受け皿クラス（{@link TrackingNumberIssuedMessage}）を渡して送ると、
     * {@code __TypeId__} には<strong>こちらのクラスパスに必ず存在する名前</strong>が載る。
     * 本番で載るのは bookingms の型名であり、この違いはワイヤ上でしか出ない。
     * <strong>相手の都合が伝わるか</strong>——往復テストが唯一確かめられるはずのものが、
     * それでは抜け落ちる（IT6 のクローズレビュー）。
     *
     * <p>bookingms の型をここから参照することはできない（BC 独立性）。JSON と
     * {@code __TypeId__} を手で組み立てて、本番と同じ形にする。
     */
    private static final String PRODUCER_TYPE_ID =
            "com.example.bookingms.application.port.TrackingNumberIssued";

    private static String payload(String trackingNumber, String bookingId, String originUnLocode) {
        return """
                {"trackingNumber": "%s", "bookingId": "%s",
                 "originUnLocode": "%s", "destinationUnLocode": "USLAX",
                 "arrivalDeadline": "2030-09-20", "occurredAt": "2026-08-22T02:00:00Z"}
                """.formatted(trackingNumber, bookingId, originUnLocode);
    }

    private void publish(String json) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader("__TypeId__", PRODUCER_TYPE_ID);
        rabbitTemplate.send(TrackingEventChannels.EXCHANGE,
                TrackingEventChannels.TRACKING_NUMBER_ISSUED,
                new Message(json.getBytes(java.nio.charset.StandardCharsets.UTF_8), properties));
    }

    /** 成功基準 2。 */
    @Test
    @DisplayName("発行されたイベントが届き、追跡の記録が残る")
    void startsTrackingWhenTheEventArrives() {
        startListening();
        String number = "TRK-20260822-9001";

        publish(payload(number, "BKG-2026000001", "JPTYO"));

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
                    // JSON をまたぐ型変換が起きるのはここだけ。NOT NULL 制約は「消える」を
                    // 捕まえるが、**1 日ずれる**ことは捕まえない
                    assertThat(activity.arrivalDeadline())
                            .isEqualTo(LocalDate.of(2030, Month.SEPTEMBER, 20));
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

        publish(payload(number, "BKG-2026000001", "JPTYO"));
        publish(payload(number, "BKG-2026000001", "JPTYO"));

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
        publish(payload("TRK-20260822-9003", "BKG-2026000002", "XXXXX"));

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
