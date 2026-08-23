package com.example.trackingms.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.shared.contract.HandlingActivityRegisteredContract;
import com.example.trackingms.domain.model.TrackingStatus;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;

/**
 * 荷役のイベント契約（<strong>コンシューマ側</strong>・[ADR-023] 決定 5）。
 *
 * <p>プロデューサ（handlingms）が送る形を、こちら側でも固定する。名簿は写しではなく、
 * <strong>両側が同じ 1 つの契約</strong>を読む。
 */
@DisplayName("荷役のイベント契約（コンシューマ側）")
class HandlingActivityRegisteredMessageContractTest {

    /**
     * <strong>本番と同じ変換器で確かめる</strong>（[ADR-022] 決定 3）。
     *
     * <p>テストが自前の {@code ObjectMapper} に寛容な設定を書くと、<strong>テストが本番より
     * 甘くなる</strong>。IT6 のクローズレビューで、コンシューマ側だけがこの罠を踏んでいた。
     */
    private final JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();

    /**
     * 本番と同じ経路で読む。
     *
     * <p><strong>プロデューサは自分の型名を {@code __TypeId__} に載せて送る</strong>。
     * この名前は<strong>こちらのクラスパスに存在しない</strong>。それでも読めるのは、
     * {@code @RabbitListener} が引数の型を「推論した型」として渡し、変換器がヘッダより
     * そちらを優先するためである。
     */
    private HandlingActivityRegisteredMessage read(String json) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader("__TypeId__", HandlingActivityRegisteredContract.PRODUCER_TYPE_ID);
        properties.setInferredArgumentType(HandlingActivityRegisteredMessage.class);
        Message message = new Message(json.getBytes(StandardCharsets.UTF_8), properties);
        return (HandlingActivityRegisteredMessage) converter.fromMessage(message);
    }

    private static final String PAYLOAD = """
            {"trackingNumber": "TRK-20260823-0001", "bookingId": "BKG-2026000001",
             "type": "LOAD", "locationUnLocode": "JPTYO",
             "completionTime": "2026-08-23T02:00:00Z", "voyageNumber": "V0100",
             "offRoute": false, "occurredAt": "2026-08-23T02:05:00Z"}
            """;

    @Test
    @DisplayName("読む項目の名簿が、合意した契約と一致する")
    void rosterIsDerivedFromTheDto() {
        assertThat(Arrays.stream(HandlingActivityRegisteredMessage.class.getRecordComponents())
                        .map(RecordComponent::getName).toList())
                .as("受け皿の項目が変わった。handlingms 側の名簿も直すこと")
                .containsExactlyElementsOf(HandlingActivityRegisteredContract.FIELDS);
    }

    /**
     * <strong>契約の全種別に、進む先が決まっている。</strong>
     *
     * <p>決まっていない種別が届くと、こちらは何もしない。例外にならないのでデッドレターにも
     * 予備の交換機にも行かず、送り手もエラーにならない。<strong>荷役は記録されているのに
     * 追跡だけが進まないまま、どこにも異常が残らない。</strong>
     */
    @ParameterizedTest
    @MethodSource("contractTypes")
    @DisplayName("契約に載る全種別に、進む先が決まっている")
    void everyContractTypeHasANextStatus(String type) {
        assertThat(TrackingStatus.afterHandling(type, false))
                .as("%s の進む先が決まっていない。荷役は記録されるのに追跡が進まない", type)
                .isPresent();
    }

    static java.util.stream.Stream<String> contractTypes() {
        return HandlingActivityRegisteredContract.TYPES.stream();
    }

    @Test
    @DisplayName("交換機とルーティングキーが、合意した契約と一致する")
    void channelNamesMatchTheContract() {
        assertThat(TrackingEventChannels.HANDLING_EXCHANGE)
                .isEqualTo(HandlingActivityRegisteredContract.EXCHANGE);
        assertThat(TrackingEventChannels.HANDLING_ACTIVITY_REGISTERED)
                .isEqualTo(HandlingActivityRegisteredContract.ROUTING_KEY);
    }

    /**
     * <strong>プロデューサの型名で届いても読める</strong>（[ADR-022] 決定 3）。
     *
     * <p>相手の型はこちらのクラスパスに無い。読めなければ全イベントがデッドレターへ落ち、
     * <strong>送り手はエラーにならない</strong>。
     */
    @Test
    @DisplayName("プロデューサの型名で届いても、こちらの受け皿で読める")
    void readsMessagesTaggedWithTheProducersType() {
        HandlingActivityRegisteredMessage message = read(PAYLOAD);

        assertThat(message.trackingNumber()).isEqualTo("TRK-20260823-0001");
        assertThat(message.type()).isEqualTo("LOAD");
        assertThat(message.locationUnLocode()).isEqualTo("JPTYO");
        assertThat(message.completionTime()).isEqualTo(Instant.parse("2026-08-23T02:00:00Z"));
        assertThat(message.offRoute()).isFalse();
    }

    /**
     * <strong>知らない項目で壊れない</strong>（[ADR-022] 決定 3）。
     *
     * <p>壊れると、handlingms が項目を 1 つ足しただけで追跡が進まなくなる。しかも
     * イベントはデッドレターに溜まるだけで、送り手はエラーにならない。
     */
    @Test
    @DisplayName("知らない項目が増えても読める")
    void ignoresUnknownFields() {
        String json = """
                {"trackingNumber": "TRK-20260823-0001", "bookingId": "BKG-2026000001",
                 "type": "LOAD", "locationUnLocode": "JPTYO",
                 "completionTime": "2026-08-23T02:00:00Z", "voyageNumber": "V0100",
                 "offRoute": false, "occurredAt": "2026-08-23T02:05:00Z",
                 "operatorName": "この項目はまだ知らない"}
                """;

        assertThatCode(() -> read(json)).doesNotThrowAnyException();
        assertThat(read(json).trackingNumber()).isEqualTo("TRK-20260823-0001");
    }

    /** 受領・引取では航海番号が無い。項目が null でも読めること。 */
    @Test
    @DisplayName("航海番号が空でも読める")
    void readsMessagesWithoutVoyageNumber() {
        String json = """
                {"trackingNumber": "TRK-20260823-0001", "bookingId": "BKG-2026000001",
                 "type": "RECEIVE", "locationUnLocode": "JPTYO",
                 "completionTime": "2026-08-23T02:00:00Z", "voyageNumber": null,
                 "offRoute": false, "occurredAt": "2026-08-23T02:05:00Z"}
                """;

        assertThat(read(json).voyageNumber()).isNull();
    }
}
