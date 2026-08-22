package com.example.bookingms.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.bookingms.application.port.TrackingNumberIssued;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 追跡番号のイベント契約（<strong>プロデューサ側</strong>・[ADR-022]）。
 *
 * <p>コンシューマ（trackingms）が読む<strong>項目名・型・交換機とルーティングキー</strong>を、
 * こちら側でも固定する。
 *
 * <p><strong>片側だけの検査では守れない。</strong>コンシューマのテストは自分で組み立てた
 * メッセージに対して緑になるため、プロデューサが項目名を変えても気づけない。ここが対に
 * なって初めて、「送っているのに届かない」を捕まえられる。<strong>しかも送り手は
 * エラーにならない</strong>——ずれても誰も気づかないのが、REST より始末が悪いところである。
 *
 * <p>名簿は手で書かず、<strong>DTO の要素から導いて写しと突き合わせる</strong>
 * （REST 契約で IT6 タスク 0.3 に入れた形）。
 */
@DisplayName("追跡番号のイベント契約（プロデューサ側）")
class TrackingNumberIssuedContractTest {

    /** コンシューマ（trackingms）が読む項目。増減したら両側を同じ変更で直す。 */
    private static final List<String> CONSUMER_EXPECTED_FIELDS = List.of(
            "trackingNumber", "bookingId", "originUnLocode", "destinationUnLocode",
            "arrivalDeadline", "occurredAt");

    /** コンシューマ側が写している交換機とルーティングキー。 */
    private static final String CONSUMER_EXPECTED_EXCHANGE = "cargoBookingChannel";

    private static final String CONSUMER_EXPECTED_ROUTING_KEY = "cargo.tracking-number-issued";

    private static final TrackingNumberIssued EVENT = new TrackingNumberIssued(
            "TRK-20260822-0001", "BKG-2026000001", "JPTYO", "USLAX",
            LocalDate.of(2030, Month.SEPTEMBER, 20), Instant.parse("2026-08-22T02:00:00Z"));

    /**
     * <strong>本番と同じ変換器で確かめる</strong>（[ADR-022] 決定 2）。
     *
     * <p>テストが自前の ObjectMapper で組み立てると、契約テストだけが通り、本物が送る形は
     * 違うという状態を素通りさせる。実際このテストを書いたとき、テスト側の設定では日付が
     * 配列（{@code [2030,9,20]}）になった。本番の変換器はそうならないが、
     * <strong>それはテスト側の設定からは分からない</strong>——だから本番の変換器を通す。
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
    @DisplayName("送る項目の名簿が、DTO の要素と一致する")
    void rosterIsDerivedFromTheDto() {
        List<String> components = Arrays.stream(TrackingNumberIssued.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(components)
                .as("イベントの項目が変わった。コンシューマ（trackingms）側の名簿も直すこと")
                .containsExactlyElementsOf(CONSUMER_EXPECTED_FIELDS);
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

        for (String field : CONSUMER_EXPECTED_FIELDS) {
            assertThat(json.has(field))
                    .as("コンシューマが読む項目 %s が無い", field)
                    .isTrue();
            assertThat(json.get(field).isNull())
                    .as("コンシューマが読む項目 %s が null。追跡を作れない", field)
                    .isFalse();
        }

        assertThat(json.get("trackingNumber").isTextual()).isTrue();
        assertThat(json.get("bookingId").isTextual()).isTrue();
        // 期限は日付、発行時刻は日時。取り違えると受け手の解釈が壊れる
        assertThat(json.get("arrivalDeadline").asText()).isEqualTo("2030-09-20");
        assertThat(Instant.parse(json.get("occurredAt").asText()))
                .isEqualTo(Instant.parse("2026-08-22T02:00:00Z"));
    }

    /**
     * 流れ先の名前は写しである。
     *
     * <p>サービスが分かれている以上、定数を共有できない。ずれると「送っているのに届かない」
     * 形で壊れ、送り手はエラーにならない。
     */
    @Test
    @DisplayName("交換機とルーティングキーが、コンシューマの写しと一致する")
    void channelNamesMatchTheConsumersCopy() {
        assertThat(CargoEventChannels.EXCHANGE).isEqualTo(CONSUMER_EXPECTED_EXCHANGE);
        assertThat(CargoEventChannels.TRACKING_NUMBER_ISSUED)
                .isEqualTo(CONSUMER_EXPECTED_ROUTING_KEY);
    }
}
