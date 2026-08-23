package com.example.trackingms;

import com.example.trackingms.application.port.LocationRepository;
import com.example.trackingms.application.port.TrackingActivityRepository;
import com.example.trackingms.infrastructure.messaging.TrackingEventChannels;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.awaitility.Awaitility;
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

/**
 * イベントが<strong>実際に届く</strong>ことを実 RabbitMQ で確かめる、往復テストの土台。
 *
 * <p>「発行するコードを書いた」「購読するコードを書いた」ことと、<strong>相手に届くこと</strong>は
 * 別である。交換機の名前・ルーティングキー・キューの結びつけ・変換器のどれか 1 つがずれると、
 * <strong>送り手はエラーにならないまま届かない</strong>。
 *
 * <p><strong>契約ごとにテストを分ける。</strong>1 つのクラスに 2 契約を入れていたが、
 * US17 で 3 契約目が入る。契約が増えるたびに同じクラスが伸びると、どの契約の何を
 * 確かめているのかが読めなくなる。土台だけを共有し、契約ごとの取り決めは各テストが持つ。
 */
@SpringBootTest
@ActiveProfiles("integration")
abstract class EventRoundTripTestBase {

    /**
     * コンテナは 1 組を共有し、止めない。
     *
     * <p>{@code @Container} の静的フィールドは<strong>それを宣言したクラスの終了時に止まる</strong>。
     * 土台を継承するテストが 2 つあると、先に終わったほうがコンテナを止め、後のほうは
     * 接続できない。起動しっぱなしにして JVM の終了に任せる（Ryuk が後片付けする）。
     */
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3-management-alpine");

    static {
        postgres.start();
        rabbitmq.start();
    }

    @Autowired
    protected RabbitTemplate rabbitTemplate;

    @Autowired
    protected RabbitAdmin rabbitAdmin;

    @Autowired
    private RabbitListenerEndpointRegistry listeners;

    @Autowired
    protected TrackingActivityRepository activities;

    @Autowired
    protected LocationRepository locations;

    protected void startListening() {
        if (!listeners.isRunning()) {
            listeners.start();
        }
    }

    /**
     * プロデューサが実際に送る形で流す。
     *
     * <p><strong>相手の型名を手で載せる。</strong>こちらの受け皿クラスを渡して送ると、
     * {@code __TypeId__} には<strong>こちらのクラスパスに必ず存在する名前</strong>が載る。
     * 本番で載るのは相手の型名であり、この違いはワイヤ上でしか出ない。
     * <strong>相手の都合が伝わるか</strong>——往復テストが唯一確かめられるはずのものが、
     * それでは抜け落ちる（IT6 のクローズレビュー）。
     */
    protected void send(String exchange, String routingKey, String producerTypeId, String json) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader("__TypeId__", producerTypeId);
        rabbitTemplate.send(exchange, routingKey,
                new Message(json.getBytes(StandardCharsets.UTF_8), properties));
    }

    /** キューに溜まった件数。まだ宣言されていなければ 0。 */
    protected long queueDepth(String queue) {
        var info = rabbitAdmin.getQueueInfo(queue);
        return info == null ? 0L : info.getMessageCount();
    }

    /** どのキューにも結びつかなかったイベントの受け皿。交換機をまたいで共通である。 */
    protected long unroutableCount() {
        return queueDepth(TrackingEventChannels.UNROUTABLE_QUEUE);
    }

    /** 変わらないことを確かめる。届かないことは、待っても起きないことでしか見えない。 */
    protected static void assertStaysTrue(org.awaitility.core.ThrowingRunnable assertion) {
        Awaitility.await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(15))
                .untilAsserted(assertion);
    }

    /** 届くまで待つ。 */
    protected static void awaitAssert(org.awaitility.core.ThrowingRunnable assertion) {
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(assertion);
    }
}
