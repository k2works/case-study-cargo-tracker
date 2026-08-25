package com.example.trackingms.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.shared.contract.CustomsStatusChangedContract;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;

/**
 * 通関のイベント契約（<strong>コンシューマ側</strong>・US29-5）。
 *
 * <p>プロデューサ（handlingms）が送る形を、こちら側でも固定する。名簿は写しではなく、
 * <strong>両側が同じ 1 つの契約</strong>を読む。
 */
@DisplayName("通関のイベント契約（コンシューマ側）")
class CustomsStatusChangedMessageContractTest {

    /**
     * <strong>本番と同じ変換器で確かめる</strong>（[ADR-022] 決定 3）。
     *
     * <p>自前の {@code ObjectMapper} に寛容な設定を書くと、テストが本番より甘くなる。
     */
    private final JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();

    private static final String PAYLOAD = """
            {"trackingNumber": "TRK-20260823-0001", "bookingId": "BKG-2026000001",
             "declarationNumber": "DEC-2026-0001", "fromStatus": "PENDING",
             "toStatus": "HELD", "reason": "書類不備のため留置", "changedBy": "tracker1",
             "changedAt": "2026-08-23T03:00:00Z", "occurredAt": "2026-08-23T03:00:05Z"}
            """;

    /**
     * 本番と同じ経路で読む。
     *
     * <p>プロデューサは自分の型名を {@code __TypeId__} に載せて送る。この名前は
     * <strong>こちらのクラスパスに存在しない</strong>。
     */
    private CustomsStatusChangedMessage read(String json) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader("__TypeId__", CustomsStatusChangedContract.PRODUCER_TYPE_ID);
        properties.setInferredArgumentType(CustomsStatusChangedMessage.class);
        Message message = new Message(json.getBytes(StandardCharsets.UTF_8), properties);
        return (CustomsStatusChangedMessage) converter.fromMessage(message);
    }

    @Test
    @DisplayName("読む項目の名簿が、合意した契約と一致する")
    void rosterIsDerivedFromTheDto() {
        assertThat(Arrays.stream(CustomsStatusChangedMessage.class.getRecordComponents())
                        .map(RecordComponent::getName).toList())
                .as("受け皿の項目が変わった。handlingms 側の名簿も直すこと")
                .containsExactlyElementsOf(CustomsStatusChangedContract.FIELDS);
    }

    @Test
    @DisplayName("交換機とルーティングキーが、合意した契約と一致する")
    void channelNamesMatchTheContract() {
        assertThat(TrackingEventChannels.HANDLING_EXCHANGE)
                .isEqualTo(CustomsStatusChangedContract.EXCHANGE);
        assertThat(TrackingEventChannels.CUSTOMS_STATUS_CHANGED)
                .isEqualTo(CustomsStatusChangedContract.ROUTING_KEY);
    }

    /**
     * <strong>プロデューサの型名で届いても読める</strong>（[ADR-022] 決定 3）。
     *
     * <p>読めなければ全イベントがデッドレターへ落ち、<strong>送り手はエラーにならない</strong>。
     */
    @Test
    @DisplayName("プロデューサの型名で届いても、こちらの受け皿で読める")
    void readsMessagesTaggedWithTheProducersType() {
        CustomsStatusChangedMessage message = read(PAYLOAD);

        assertThat(message.trackingNumber()).isEqualTo("TRK-20260823-0001");
        assertThat(message.toStatus()).isEqualTo("HELD");
        assertThat(message.reason())
                .as("理由が落ちると、担当者は何があって留め置かれたか分からない")
                .isEqualTo("書類不備のため留置");
        assertThat(message.changedAt()).isEqualTo(Instant.parse("2026-08-23T03:00:00Z"));
    }

    @Test
    @DisplayName("知らない項目が増えても読める")
    void ignoresUnknownFields() {
        String json = PAYLOAD.replace("\"occurredAt\"", "\"customsOffice\": \"まだ知らない\", \"occurredAt\"");

        assertThatCode(() -> read(json)).doesNotThrowAnyException();
        assertThat(read(json).toStatus()).isEqualTo("HELD");
    }

    /**
     * <strong>リスナーは受け取った値をそのままユースケースへ渡す</strong>。
     *
     * <p>ここで項目を取り違えると（例: {@code fromStatus} を渡す）、留置になった瞬間に
     * 起票されず、逆に留置から出た瞬間に起票される。どちらも例外にならない。
     */
    @Test
    @DisplayName("リスナーは、受け取った項目をそのままユースケースへ渡す")
    void listenerPassesTheMessageThrough() {
        List<String> received = new ArrayList<>();
        CustomsStatusChangedListener listener = new CustomsStatusChangedListener(
                new com.example.trackingms.application.internal.DetectCustomsHoldUseCase(
                        null, null) {
                    @Override
                    public void onCustomsStatusChanged(String trackingNumber, String toStatus,
                            String reason, Instant changedAt) {
                        received.add(trackingNumber);
                        received.add(toStatus);
                        received.add(reason);
                        received.add(changedAt.toString());
                    }
                });

        listener.onCustomsStatusChanged(read(PAYLOAD));

        assertThat(received).containsExactly("TRK-20260823-0001", "HELD", "書類不備のため留置",
                "2026-08-23T03:00:00Z");
    }
}
