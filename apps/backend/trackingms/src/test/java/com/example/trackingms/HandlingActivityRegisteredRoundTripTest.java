package com.example.trackingms;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.trackingms.domain.model.TrackingNumber;
import com.example.trackingms.domain.model.TrackingStatus;
import com.example.trackingms.infrastructure.messaging.TrackingEventChannels;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 「荷役作業を記録した」の往復（US15-4・[ADR-023] 決定 5・成功基準 1）。
 *
 * <p>2 本目の非同期連携である。handlingms が送り、こちらが追跡の状態を進める。
 */
@DisplayName("荷役のイベントの往復（実 RabbitMQ）")
class HandlingActivityRegisteredRoundTripTest extends EventRoundTripTestBase {

    /** handlingms の型名。<strong>こちらのクラスパスには存在しない</strong>。 */
    private static final String PRODUCER_TYPE_ID =
            "com.example.handlingms.application.port.HandlingActivityRegistered";

    private static final String BOOKING_ID = "BKG-2026000005";

    /** 追跡はまず 1 本目の契約で作る。荷役だけでは追う相手が無い。 */
    private void startTracking(String trackingNumber) {
        send(TrackingEventChannels.EXCHANGE, TrackingEventChannels.TRACKING_NUMBER_ISSUED,
                "com.example.bookingms.application.internal.outboundservices.acl.TrackingNumberIssued", """
                        {"trackingNumber": "%s", "bookingId": "%s",
                         "originUnLocode": "JPTYO", "destinationUnLocode": "USLAX",
                         "arrivalDeadline": "2030-09-20", "estimatedArrival": null,
                         "occurredAt": "2026-08-22T02:00:00Z"}
                        """.formatted(trackingNumber, BOOKING_ID));
        awaitAssert(() -> assertThat(
                activities.findByTrackingNumber(TrackingNumber.of(trackingNumber))).isPresent());
    }

    private void publishHandling(String trackingNumber, String type, String unLocode) {
        send(TrackingEventChannels.HANDLING_EXCHANGE,
                TrackingEventChannels.HANDLING_ACTIVITY_REGISTERED, PRODUCER_TYPE_ID, """
                        {"trackingNumber": "%s", "bookingId": "%s",
                         "type": "%s", "locationUnLocode": "%s",
                         "completionTime": "2026-08-23T02:00:00Z", "voyageNumber": null,
                         "offRoute": false, "occurredAt": "2026-08-23T02:05:00Z"}
                        """.formatted(trackingNumber, BOOKING_ID, type, unLocode));
    }

    private void awaitStatus(String number, TrackingStatus expected) {
        awaitAssert(() -> assertThat(activities.findByTrackingNumber(TrackingNumber.of(number))
                .orElseThrow().trackingStatus())
                .as("荷役は届いたのに追跡が %s へ進んでいない", expected)
                .isEqualTo(expected));
    }

    private long handlingDeadLetterCount() {
        return queueDepth(TrackingEventChannels.HANDLING_DEAD_LETTER_QUEUE);
    }

    @Test
    @DisplayName("荷役のイベントが届くと、追跡の状態が進む")
    void advancesTrackingWhenHandlingArrives() {
        startListening();
        String number = "TRK-20260822-9006";
        startTracking(number);

        publishHandling(number, "RECEIVE", "JPTYO");
        awaitStatus(number, TrackingStatus.RECEIVED);

        publishHandling(number, "LOAD", "JPTYO");
        awaitStatus(number, TrackingStatus.LOADED);

        // 途中の港での荷降しは、次の積込を待つ
        publishHandling(number, "UNLOAD", "CNSHA");
        awaitStatus(number, TrackingStatus.UNLOADED);

        // 目的港での荷降しは、次の積込ではなく荷受人の引取を待つ
        publishHandling(number, "UNLOAD", "USLAX");
        awaitStatus(number, TrackingStatus.AWAITING_CLAIM);

        publishHandling(number, "CLAIM", "USLAX");
        awaitStatus(number, TrackingStatus.CLAIMED);

        // **戻せる遷移は作らない。**送り直す手段（dev:k8s:events:redeliver）がある以上、
        // 届く順が入れ替わる経路は実際に存在する。巻き戻ると、荷主は「引取済だったはずの
        // 貨物が受領待ちに戻っている」を見る
        publishHandling(number, "RECEIVE", "JPTYO");

        assertStaysTrue(() -> assertThat(
                activities.findByTrackingNumber(TrackingNumber.of(number))
                        .orElseThrow().trackingStatus())
                .as("古い荷役の再配送で追跡が巻き戻った")
                .isEqualTo(TrackingStatus.CLAIMED));
    }

    /**
     * <strong>知らない種別で止めない。ただし追跡も動かさない。</strong>
     *
     * <p>相手が種別を足したときに例外にすると、その種別 1 つで後続の荷役まで止まる。
     * 一方で「何か起きる」ことも許さない——知らない種別を進む先の分からないまま
     * 反映すると、荷主は理由の説明できない状態を見る。
     */
    @Test
    @DisplayName("知らない種別が届いても、止まらず、状態も動かない")
    void neitherFailsNorMovesForAnUnknownType() {
        startListening();
        String number = "TRK-20260822-9008";
        startTracking(number);
        publishHandling(number, "RECEIVE", "JPTYO");
        awaitStatus(number, TrackingStatus.RECEIVED);
        long deadLettersBefore = handlingDeadLetterCount();

        publishHandling(number, "CUSTOMS_INSPECTION", "JPTYO");

        assertStaysTrue(() -> {
            assertThat(activities.findByTrackingNumber(TrackingNumber.of(number))
                    .orElseThrow().trackingStatus())
                    .as("知らない種別で追跡が動いた")
                    .isEqualTo(TrackingStatus.RECEIVED);
            assertThat(handlingDeadLetterCount())
                    .as("知らない種別でデッドレターへ回った。種別 1 つで後続の荷役まで止まる")
                    .isEqualTo(deadLettersBefore);
        });
    }

    /**
     * 成功基準 3 を荷役の経路でも確かめる。
     *
     * <p>デッドレターの設定を書いたことと、落ちたイベントがそこへ届くことは別である。
     */
    @Test
    @DisplayName("処理できなかった荷役のイベントはデッドレターに残る")
    void movesUnprocessableHandlingEventsToTheDeadLetterQueue() {
        startListening();
        long before = handlingDeadLetterCount();

        // 日時として読めない値。**知らない追跡番号では落とさない**（それは運用の照会が拾う）
        // ので、変換そのものが成り立たない入力を送る
        send(TrackingEventChannels.HANDLING_EXCHANGE,
                TrackingEventChannels.HANDLING_ACTIVITY_REGISTERED, PRODUCER_TYPE_ID, """
                        {"trackingNumber": "TRK-20260822-9007", "bookingId": "BKG-2026000005",
                         "type": "RECEIVE", "locationUnLocode": "JPTYO",
                         "completionTime": "きのう", "voyageNumber": null,
                         "offRoute": false, "occurredAt": "2026-08-23T02:05:00Z"}
                        """);

        awaitAssert(() -> assertThat(handlingDeadLetterCount())
                .as("処理できなかったイベントがどこにも残っていない")
                .isGreaterThan(before));
    }
}
