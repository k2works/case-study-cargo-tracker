package com.example.trackingms.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;

/**
 * 追跡番号のイベント契約（<strong>コンシューマ側</strong>・[ADR-022]）。
 *
 * <p>プロデューサ（bookingms）が送る形を、こちら側でも固定する。名簿はプロデューサ側の
 * <strong>写し</strong>であり、DTO の要素から導いて突き合わせる。
 */
@DisplayName("追跡番号のイベント契約（コンシューマ側）")
class TrackingNumberIssuedMessageContractTest {

    /** プロデューサ（bookingms）が送る項目。増減したら両側を同じ変更で直す。 */
    private static final List<String> PRODUCER_EXPECTED_FIELDS = List.of(
            "trackingNumber", "bookingId", "originUnLocode", "destinationUnLocode",
            "arrivalDeadline", "occurredAt");

    /** プロデューサ側が持つ交換機とルーティングキー。 */
    private static final String PRODUCER_EXCHANGE = "cargoBookingChannel";

    private static final String PRODUCER_ROUTING_KEY = "cargo.tracking-number-issued";

    /**
     * <strong>本番と同じ変換器で確かめる</strong>（[ADR-022] 決定 3）。
     *
     * <p>テストが自前の {@code ObjectMapper} に寛容な設定を書くと、<strong>テストが本番より
     * 甘くなる</strong>。本番側を厳格に変えても（あるいは元から厳格でも）緑のままで、
     * 決定 3 は実質未検証になる。プロデューサ側の契約テストが戒めているのと同じ罠を、
     * コンシューマ側だけが踏んでいた（IT6 のクローズレビュー）。
     */
    private final JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();

    /**
     * 本番と同じ経路で読む。
     *
     * <p><strong>プロデューサは自分の型名を `__TypeId__` に載せて送る</strong>
     * （{@code com.example.bookingms.application.port.TrackingNumberIssued}）。この名前は
     * <strong>こちらのクラスパスに存在しない</strong>。それでも読めるのは、
     * {@code @RabbitListener} が引数の型を「推論した型」として渡し、変換器がヘッダより
     * そちらを優先するためである。
     *
     * <p>ここで推論した型を渡さずに読むと {@code ClassNotFoundException} になる。
     * <strong>つまりこの経路は、本番の形を写して初めて意味を持つ。</strong>
     */
    private TrackingNumberIssuedMessage read(String json) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader("__TypeId__",
                "com.example.bookingms.application.port.TrackingNumberIssued");
        properties.setInferredArgumentType(TrackingNumberIssuedMessage.class);
        Message message = new Message(json.getBytes(StandardCharsets.UTF_8), properties);
        return (TrackingNumberIssuedMessage) converter.fromMessage(message);
    }

    @Test
    @DisplayName("読む項目の名簿が、DTO の要素と一致する")
    void rosterIsDerivedFromTheDto() {
        List<String> components = Arrays.stream(
                        TrackingNumberIssuedMessage.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(components)
                .as("受け皿の項目が変わった。プロデューサ（bookingms）側の名簿も直すこと")
                .containsExactlyElementsOf(PRODUCER_EXPECTED_FIELDS);
    }

    @Test
    @DisplayName("交換機とルーティングキーが、プロデューサの値と一致する")
    void channelNamesMatchTheProducer() {
        assertThat(TrackingEventChannels.EXCHANGE).isEqualTo(PRODUCER_EXCHANGE);
        assertThat(TrackingEventChannels.TRACKING_NUMBER_ISSUED).isEqualTo(PRODUCER_ROUTING_KEY);
    }

    /**
     * <strong>知らない項目で壊れない</strong>（[ADR-022] 決定 3）。
     *
     * <p>壊れると、プロデューサが項目を 1 つ足しただけで追跡が作られなくなる。しかも
     * イベントはデッドレターに溜まるだけで、送り手はエラーにならない。
     */
    /**
     * <strong>プロデューサの型名で届いても読める</strong>（[ADR-022] 決定 3）。
     *
     * <p>相手の型はこちらのクラスパスに無い。読めなければ全イベントがデッドレターへ落ち、
     * <strong>送り手はエラーにならない</strong>。相手の型を共有しないという判断
     * （ACL）が成り立つ根拠がここにある。
     */
    @Test
    @DisplayName("プロデューサの型名で届いても、こちらの受け皿で読める")
    void readsMessagesTaggedWithTheProducersType() {
        String json = """
                {"trackingNumber": "TRK-20260822-0001", "bookingId": "BKG-2026000001",
                 "originUnLocode": "JPTYO", "destinationUnLocode": "USLAX",
                 "arrivalDeadline": "2030-09-20", "occurredAt": "2026-08-22T02:00:00Z"}
                """;

        assertThat(read(json).trackingNumber()).isEqualTo("TRK-20260822-0001");
    }

    @Test
    @DisplayName("知らない項目が増えても読める")
    void ignoresUnknownFields() throws Exception {
        String json = """
                {"trackingNumber": "TRK-20260822-0001", "bookingId": "BKG-2026000001",
                 "originUnLocode": "JPTYO", "destinationUnLocode": "USLAX",
                 "arrivalDeadline": "2030-09-20", "occurredAt": "2026-08-22T02:00:00Z",
                 "shipperName": "この項目はまだ知らない"}
                """;

        assertThatCode(() -> read(json)).doesNotThrowAnyException();

        TrackingNumberIssuedMessage message = read(json);
        assertThat(message.trackingNumber()).isEqualTo("TRK-20260822-0001");
        assertThat(message.arrivalDeadline()).isEqualTo(LocalDate.of(2030, Month.SEPTEMBER, 20));
        assertThat(message.occurredAt()).isEqualTo(Instant.parse("2026-08-22T02:00:00Z"));
    }
}
