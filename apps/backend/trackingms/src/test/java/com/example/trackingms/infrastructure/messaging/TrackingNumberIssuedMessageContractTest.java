package com.example.trackingms.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            // 知らない項目は無視する（ADR-022 決定 3）
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

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
    @Test
    @DisplayName("知らない項目が増えても読める")
    void ignoresUnknownFields() throws Exception {
        String json = """
                {"trackingNumber": "TRK-20260822-0001", "bookingId": "BKG-2026000001",
                 "originUnLocode": "JPTYO", "destinationUnLocode": "USLAX",
                 "arrivalDeadline": "2030-09-20", "occurredAt": "2026-08-22T02:00:00Z",
                 "shipperName": "この項目はまだ知らない"}
                """;

        assertThatCode(() -> objectMapper.readValue(json, TrackingNumberIssuedMessage.class))
                .doesNotThrowAnyException();

        TrackingNumberIssuedMessage message =
                objectMapper.readValue(json, TrackingNumberIssuedMessage.class);
        assertThat(message.trackingNumber()).isEqualTo("TRK-20260822-0001");
        assertThat(message.arrivalDeadline()).isEqualTo(LocalDate.of(2030, Month.SEPTEMBER, 20));
        assertThat(message.occurredAt()).isEqualTo(Instant.parse("2026-08-22T02:00:00Z"));
    }
}
