package com.example.bookingms;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.bookingms.domain.repository.CargoRepository;
import com.example.bookingms.domain.repository.CargoSummary;
import com.example.bookingms.domain.model.valueobjects.BookingStatus;
import com.example.bookingms.domain.model.aggregates.Cargo;
import com.example.bookingms.domain.model.valueobjects.CargoType;
import com.example.shared.contract.HandlingActivityRegisteredContract;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * 荷役のイベントで予約が輸送中へ進むことを、<strong>実際のブローカーで</strong>確かめる
 * （[ADR-025] 決定 1）。
 *
 * <p>「購読するコードを書いた」ことと<strong>相手に届くこと</strong>は別である。
 * 交換機の名前・ルーティングキー・キューの結びつけ・変換器のどれか 1 つがずれると、
 * <strong>送り手はエラーにならないまま届かない</strong>。
 *
 * <p><strong>この購読が無いあいだ、予約一覧は船に載った貨物を「受領待ち」と出し続けて
 * いた。</strong>`transport_status` は IT2（[ADR-009]）からあるのに、更新する者が誰も
 * いなかった——7 イテレーションのあいだ、誰も気づかなかった。
 */
@DisplayName("荷役のイベントで予約が進む")
class HandlingActivityRegisteredRoundTripTest extends CargoPersistenceTestBase {

    /**
     * 相手（handlingms）が実際に載せる型名。
     *
     * <p><strong>こちらの受け皿クラスを渡して送らない。</strong>それでは
     * {@code __TypeId__} にこちらのクラスパスに存在する名前が載り、
     * <strong>相手の都合が伝わるか</strong>という往復テストの主眼が抜け落ちる。
     */
    private static final String PRODUCER_TYPE_ID =
            "com.example.handlingms.application.port.HandlingActivityRegistered";

    @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3-management-alpine");

    static {
        rabbitmq.start();
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitListenerEndpointRegistry listeners;

    @Autowired
    private CargoRepository cargoes;

    /**
     * 追跡番号まで発行した予約を作る。
     *
     * <p><strong>本番と同じ経路を通す。</strong>状態を直接組み立てて保存すると、確定を
     * 経ずに追跡番号が付いた行ができ、実際には起こらない前提で検査することになる。
     */
    private String trackedCargo(String email) {
        Long shipper = shipperId("荷役の受け手", email);
        Cargo booked = bookCargo.book(command(shipper, CargoType.GENERAL));
        Cargo routed = cargoes.save(
                booked.requestRouting().assignItinerary(itineraryVia("CNSHA", "Shanghai"), LA));
        Cargo confirmed = cargoes.save(cargoes
                .save(routed.notifyShipper(java.time.Instant.parse("2026-08-22T02:00:00Z"),
                        "sales01"))
                .confirm());

        return issueTrackingNumber.issue(confirmed.bookingId().orElseThrow().value())
                .orElseThrow()
                .trackingNumber().orElseThrow().value();
    }

    private void publishHandling(String trackingNumber, String type, String unLocode) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader("__TypeId__", PRODUCER_TYPE_ID);
        String json = """
                {"trackingNumber": "%s", "bookingId": "BKG-2026000001",
                 "type": "%s", "locationUnLocode": "%s",
                 "completionTime": "2026-08-23T02:00:00Z", "voyageNumber": null,
                 "offRoute": false, "occurredAt": "2026-08-23T02:05:00Z"}
                """.formatted(trackingNumber, type, unLocode);
        // **契約の値で送る。**こちらの定数で送ると、その定数がずれても届いてしまい、
        // 「綴りを間違えたら届かない」ことを一度も確かめられない（実際にそうなっていた）
        rabbitTemplate.send(HandlingActivityRegisteredContract.EXCHANGE,
                HandlingActivityRegisteredContract.ROUTING_KEY,
                new Message(json.getBytes(StandardCharsets.UTF_8), properties));
    }

    private BookingStatus statusOf(String trackingNumber) {
        return cargoes.findByTrackingNumber(trackingNumber)
                .map(CargoSummary::cargo)
                .map(Cargo::bookingStatus)
                .orElse(null);
    }

    private void awaitStatus(String trackingNumber, BookingStatus expected) {
        Awaitility.await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(statusOf(trackingNumber)).isEqualTo(expected));
    }

    @Test
    @DisplayName("積込のイベントが届くと、予約が輸送中になる")
    void advancesTheBookingWhenTheEventArrives() {
        if (!listeners.isRunning()) {
            listeners.start();
        }
        String trackingNumber = trackedCargo("roundtrip-load@example.com");

        publishHandling(trackingNumber, "LOAD", "JPTYO");

        awaitStatus(trackingNumber, BookingStatus.IN_TRANSIT);
        assertThat(cargoes.findByTrackingNumber(trackingNumber).orElseThrow().cargo()
                .lastHandlingLocation())
                .as("最後の荷役地点が行に残っていない。陸揚げ地の候補に現在地が出せない")
                .contains("JPTYO");
    }

    /**
     * <strong>巻き戻さない。</strong>
     *
     * <p>デッドレターからの送り直しで荷役の届く順は入れ替わる。<strong>変わらないことは、
     * 待っても起きないことでしか見えない</strong>。
     */
    @Test
    @DisplayName("引取のあとに古い積込が届いても、配送完了のまま")
    void neverRegressesTheBookingStatus() {
        if (!listeners.isRunning()) {
            listeners.start();
        }
        String trackingNumber = trackedCargo("roundtrip-regress@example.com");
        publishHandling(trackingNumber, "LOAD", "JPTYO");
        awaitStatus(trackingNumber, BookingStatus.IN_TRANSIT);
        publishHandling(trackingNumber, "CLAIM", "USLAX");
        awaitStatus(trackingNumber, BookingStatus.DELIVERED);

        publishHandling(trackingNumber, "LOAD", "JPTYO");

        Awaitility.await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(15))
                .untilAsserted(() ->
                        assertThat(statusOf(trackingNumber)).isEqualTo(BookingStatus.DELIVERED));
    }
}
