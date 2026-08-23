package com.example.handlingms.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.handlingms.application.port.HandlingActivityRegistered;
import com.example.handlingms.domain.model.HandlingType;
import com.example.shared.contract.HandlingActivityRegisteredContract;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;

/**
 * 荷役のイベント契約（<strong>プロデューサ側</strong>・[ADR-023] 決定 5）。
 *
 * <p><strong>片側だけの検査では守れない。</strong>コンシューマのテストは自分で組み立てた
 * メッセージに対して緑になるため、プロデューサが項目名を変えても気づけない。
 * <strong>しかも送り手はエラーにならない</strong>——ずれても誰も気づかないのが、REST より
 * 始末が悪いところである。
 */
@DisplayName("荷役のイベント契約（プロデューサ側）")
class HandlingActivityRegisteredContractTest {

    private static final HandlingActivityRegistered EVENT = new HandlingActivityRegistered(
            "TRK-20260823-0001", "BKG-2026000001", "LOAD", "JPTYO",
            Instant.parse("2026-08-23T02:00:00Z"), "V0100", false,
            Instant.parse("2026-08-23T02:05:00Z"));

    /**
     * <strong>本番と同じ変換器で確かめる</strong>（[ADR-022] 決定 2）。
     *
     * <p>テストが自前の {@code ObjectMapper} で組み立てると、契約テストだけが通り、
     * 本物が送る形は違うという状態を素通りさせる。
     */
    private final JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();

    private final ObjectMapper reader = new ObjectMapper();

    private JsonNode sentJson() throws Exception {
        Message message = converter.toMessage(EVENT, new MessageProperties());
        return reader.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
    }

    /**
     * <strong>名簿を手で書かない。</strong>
     *
     * <p>手書きの名簿は、こちらが項目を足しても赤にならない。足した項目をコンシューマが
     * 読めているかは誰も確かめておらず、実物でだけ null になる。
     */
    @Test
    @DisplayName("送る項目の名簿が、合意した契約と一致する")
    void rosterIsDerivedFromTheDto() {
        assertThat(Arrays.stream(HandlingActivityRegistered.class.getRecordComponents())
                        .map(RecordComponent::getName).toList())
                .as("イベントの項目が変わった。trackingms 側の受け皿も直すこと")
                .containsExactlyElementsOf(HandlingActivityRegisteredContract.FIELDS);
    }

    /**
     * <strong>送る語彙が、合意した契約と一致する。</strong>
     *
     * <p>種別を足しても項目名は変わらないので、名簿の検査は緑のままである。
     * 受け手は知らない種別で何もせず、例外も出ないため誰も気づかない。
     */
    @Test
    @DisplayName("送る種別の語彙が、合意した契約と一致する")
    void vocabularyMatchesTheContract() {
        assertThat(Arrays.stream(HandlingType.values()).map(Enum::name).toList())
                .as("荷役の種別が変わった。trackingms 側の遷移も直すこと")
                .containsExactlyElementsOf(HandlingActivityRegisteredContract.TYPES);
    }

    @Test
    @DisplayName("交換機とルーティングキーが、合意した契約と一致する")
    void channelNamesMatchTheContract() {
        assertThat(HandlingEventChannels.EXCHANGE)
                .isEqualTo(HandlingActivityRegisteredContract.EXCHANGE);
        assertThat(HandlingEventChannels.HANDLING_ACTIVITY_REGISTERED)
                .isEqualTo(HandlingActivityRegisteredContract.ROUTING_KEY);
    }

    /**
     * 流れる JSON が、コンシューマの読む形になっている。
     *
     * <p>項目の存在だけでなく<strong>型・形式</strong>まで見る。日時がエポックミリ秒に
     * 変わっても、存在だけを見る検査は緑のままで、受け手は実物でだけ落ちる。
     */
    @Test
    @DisplayName("JSON はコンシューマが解釈できる形で送る")
    void serializesInTheShapeTheConsumerReads() throws Exception {
        JsonNode json = sentJson();

        for (String field : HandlingActivityRegisteredContract.FIELDS) {
            assertThat(json.has(field))
                    .as("コンシューマが読む項目 %s が無い", field)
                    .isTrue();
        }
        assertThat(json.get("type").asText()).isEqualTo("LOAD");
        assertThat(json.get("offRoute").isBoolean())
                .as("予定外だったかは真偽で送る。文字列にすると受け手の解釈が分かれる")
                .isTrue();
        assertThat(Instant.parse(json.get("completionTime").asText()))
                .isEqualTo(Instant.parse("2026-08-23T02:00:00Z"));
    }

    /**
     * <strong>航海番号は空でも項目ごと消さない。</strong>
     *
     * <p>受領・引取では航海番号が無い。項目ごと消すと、受け手は「知らない項目」ではなく
     * 「無い項目」を読むことになり、実装によっては例外になる。
     */
    @Test
    @DisplayName("航海番号が無い作業でも、項目は残す")
    void keepsNullVoyageNumberAsAField() throws Exception {
        HandlingActivityRegistered received = new HandlingActivityRegistered(
                "TRK-20260823-0001", "BKG-2026000001", "RECEIVE", "JPTYO",
                Instant.parse("2026-08-23T02:00:00Z"), null, false,
                Instant.parse("2026-08-23T02:05:00Z"));
        Message message = converter.toMessage(received, new MessageProperties());
        JsonNode json = reader.readTree(new String(message.getBody(), StandardCharsets.UTF_8));

        assertThat(json.has("voyageNumber")).isTrue();
        assertThat(json.get("voyageNumber").isNull()).isTrue();
    }
}
