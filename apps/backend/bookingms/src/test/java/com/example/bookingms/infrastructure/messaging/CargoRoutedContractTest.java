package com.example.bookingms.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.bookingms.application.port.CargoRouted;
import com.example.shared.contract.CargoRoutedContract;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 経路のイベント契約（<strong>プロデューサ側</strong>・[ADR-024] 決定 4）。
 *
 * <p><strong>3 本目の契約である。</strong>片側だけの検査では守れない——コンシューマの
 * テストは自分で組み立てたメッセージに対して緑になるため、プロデューサが項目名を
 * 変えても気づけない。<strong>しかも送り手はエラーにならない</strong>。
 */
@DisplayName("経路のイベント契約（プロデューサ側）")
class CargoRoutedContractTest {

    private static final CargoRouted EVENT = new CargoRouted("TRK-20260822-0001",
            "BKG-2026000001", LocalDate.of(2027, Month.SEPTEMBER, 15),
            Instant.parse("2026-08-22T02:00:00Z"));

    /** <strong>本番と同じ変換器で確かめる</strong>（[ADR-022] 決定 2）。 */
    private final JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();

    private final ObjectMapper reader = new ObjectMapper();

    private JsonNode sentJson() throws Exception {
        Message message = converter.toMessage(EVENT, new MessageProperties());
        return reader.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("送る項目の名簿が、DTO の要素と一致する")
    void rosterIsDerivedFromTheDto() {
        List<String> components = Arrays.stream(CargoRouted.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(components)
                .as("イベントの項目が変わった。コンシューマ（trackingms）側の名簿も直すこと")
                .containsExactlyElementsOf(CargoRoutedContract.FIELDS);
    }

    /**
     * 流れる JSON が、コンシューマの読む形になっている。
     *
     * <p><strong>到着の見込みは日付である。</strong>日時で送ると、受け手は時差の分だけ
     * 1 日ずれた日付を出す（[ADR-010]）。
     */
    @Test
    @DisplayName("JSON はコンシューマが解釈できる形で送る")
    void serializesInTheShapeTheConsumerReads() throws Exception {
        JsonNode json = sentJson();

        for (String field : CargoRoutedContract.FIELDS) {
            assertThat(json.has(field))
                    .as("コンシューマが読む項目 %s が無い", field)
                    .isTrue();
            assertThat(json.get(field).isNull())
                    .as("コンシューマが読む項目 %s が null。到着の見込みを持てない", field)
                    .isFalse();
        }

        assertThat(json.get("trackingNumber").isTextual()).isTrue();
        assertThat(json.get("estimatedArrival").asText())
                .as("到着の見込みが日付になっていない")
                .isEqualTo("2027-09-15");
        assertThat(Instant.parse(json.get("occurredAt").asText()))
                .isEqualTo(Instant.parse("2026-08-22T02:00:00Z"));
    }

    @Test
    @DisplayName("交換機とルーティングキーが、合意した契約と一致する")
    void channelNamesMatchTheContract() {
        assertThat(CargoEventChannels.EXCHANGE).isEqualTo(CargoRoutedContract.EXCHANGE);
        assertThat(CargoEventChannels.CARGO_ROUTED).isEqualTo(CargoRoutedContract.ROUTING_KEY);
    }

    /**
     * <strong>型名が契約と一致する。</strong>
     *
     * <p>この名前はコンシューマのクラスパスに存在しない。それでも読めることが
     * 「相手の型を共有しない」判断の根拠である。
     */
    @Test
    @DisplayName("型名が、合意した契約と一致する")
    void typeIdMatchesTheContract() {
        assertThat(CargoRouted.class.getName()).isEqualTo(CargoRoutedContract.PRODUCER_TYPE_ID);
    }
}
