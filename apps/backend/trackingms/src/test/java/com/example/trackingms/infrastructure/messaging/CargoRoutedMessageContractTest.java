package com.example.trackingms.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.shared.contract.CargoRoutedContract;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Month;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;

/**
 * 経路のイベント契約（<strong>コンシューマ側</strong>・[ADR-024] 決定 4）。
 *
 * <p>プロデューサ（bookingms）が送る形を、こちら側でも固定する。名簿は写しではなく、
 * <strong>両側が同じ 1 つの契約</strong>を読む。
 */
@DisplayName("経路のイベント契約（コンシューマ側）")
class CargoRoutedMessageContractTest {

    /** <strong>本番と同じ変換器で確かめる</strong>（[ADR-022] 決定 3）。 */
    private final JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();

    /**
     * 本番と同じ経路で読む。
     *
     * <p><strong>プロデューサは自分の型名を {@code __TypeId__} に載せて送る。</strong>
     * この名前は<strong>こちらのクラスパスに存在しない</strong>。それでも読めるのは、
     * {@code @RabbitListener} が引数の型を「推論した型」として渡し、変換器がヘッダより
     * そちらを優先するためである。
     */
    private CargoRoutedMessage read(String json) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader("__TypeId__", CargoRoutedContract.PRODUCER_TYPE_ID);
        properties.setInferredArgumentType(CargoRoutedMessage.class);
        Message message = new Message(json.getBytes(StandardCharsets.UTF_8), properties);
        return (CargoRoutedMessage) converter.fromMessage(message);
    }

    private static final String PAYLOAD = """
            {"trackingNumber": "TRK-20260822-0001", "bookingId": "BKG-2026000001",
             "estimatedArrival": "2027-09-15", "occurredAt": "2026-08-22T02:00:00Z"}
            """;

    @Test
    @DisplayName("読む項目の名簿が、合意した契約と一致する")
    void rosterIsDerivedFromTheDto() {
        assertThat(Arrays.stream(CargoRoutedMessage.class.getRecordComponents())
                        .map(RecordComponent::getName).toList())
                .as("受け皿の項目が変わった。bookingms 側の名簿も直すこと")
                .containsExactlyElementsOf(CargoRoutedContract.FIELDS);
    }

    @Test
    @DisplayName("交換機とルーティングキーが、合意した契約と一致する")
    void channelNamesMatchTheContract() {
        assertThat(TrackingEventChannels.EXCHANGE).isEqualTo(CargoRoutedContract.EXCHANGE);
        assertThat(TrackingEventChannels.CARGO_ROUTED)
                .isEqualTo(CargoRoutedContract.ROUTING_KEY);
    }

    /**
     * <strong>プロデューサの型名で届いても読める</strong>（[ADR-022] 決定 3）。
     *
     * <p>読めなければ全イベントがデッドレターへ落ち、<strong>送り手はエラーにならない</strong>。
     */
    @Test
    @DisplayName("プロデューサの型名で届いても、こちらの受け皿で読める")
    void readsMessagesTaggedWithTheProducersType() {
        CargoRoutedMessage message = read(PAYLOAD);

        assertThat(message.trackingNumber()).isEqualTo("TRK-20260822-0001");
        assertThat(message.bookingId()).isEqualTo("BKG-2026000001");
        // **日付として読む。**日時で読むと、時差の分だけ 1 日ずれる（[ADR-010]）
        assertThat(message.estimatedArrival())
                .isEqualTo(LocalDate.of(2027, Month.SEPTEMBER, 15));
    }

    /**
     * <strong>知らない項目で壊れない</strong>（[ADR-022] 決定 3）。
     *
     * <p>壊れると、bookingms が項目を 1 つ足しただけで到着の見込みが届かなくなる。
     */
    @Test
    @DisplayName("知らない項目が増えても読める")
    void ignoresUnknownFields() {
        String json = """
                {"trackingNumber": "TRK-20260822-0001", "bookingId": "BKG-2026000001",
                 "estimatedArrival": "2027-09-15", "occurredAt": "2026-08-22T02:00:00Z",
                 "expectedDeparture": "2027-09-02"}
                """;

        assertThatCode(() -> read(json)).doesNotThrowAnyException();
        assertThat(read(json).trackingNumber()).isEqualTo("TRK-20260822-0001");
    }
}
