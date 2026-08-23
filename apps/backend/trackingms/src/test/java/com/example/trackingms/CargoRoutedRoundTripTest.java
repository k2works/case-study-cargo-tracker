package com.example.trackingms;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.trackingms.domain.model.TrackingNumber;
import com.example.trackingms.infrastructure.messaging.TrackingEventChannels;
import java.time.LocalDate;
import java.time.Month;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 「経路が決まった」の往復（[ADR-024] 決定 4・US18-2）。
 *
 * <p><strong>3 本目の非同期連携である。</strong>bookingms が旅程から日付 1 つを出し、
 * こちらがそれを持つ。届かなければ、荷主の追跡照会に推定到着日が出せない。
 */
@DisplayName("経路のイベントの往復（実 RabbitMQ）")
class CargoRoutedRoundTripTest extends EventRoundTripTestBase {

    /** bookingms の型名。<strong>こちらのクラスパスには存在しない</strong>。 */
    private static final String PRODUCER_TYPE_ID =
            "com.example.bookingms.application.port.CargoRouted";

    private static final String BOOKING_ID = "BKG-2026000009";

    private void startTracking(String trackingNumber) {
        send(TrackingEventChannels.EXCHANGE, TrackingEventChannels.TRACKING_NUMBER_ISSUED,
                "com.example.bookingms.application.port.TrackingNumberIssued", """
                        {"trackingNumber": "%s", "bookingId": "%s",
                         "originUnLocode": "JPTYO", "destinationUnLocode": "USLAX",
                         "arrivalDeadline": "2030-09-20", "occurredAt": "2026-08-22T02:00:00Z"}
                        """.formatted(trackingNumber, BOOKING_ID));
        awaitAssert(() -> assertThat(
                activities.findByTrackingNumber(TrackingNumber.of(trackingNumber))).isPresent());
    }

    private void publishRouted(String trackingNumber, String estimatedArrival) {
        send(TrackingEventChannels.EXCHANGE, TrackingEventChannels.CARGO_ROUTED,
                PRODUCER_TYPE_ID, """
                        {"trackingNumber": "%s", "bookingId": "%s",
                         "estimatedArrival": "%s", "occurredAt": "2026-08-22T02:05:00Z"}
                        """.formatted(trackingNumber, BOOKING_ID, estimatedArrival));
    }

    private long deadLetterCount() {
        return queueDepth(TrackingEventChannels.CARGO_ROUTED_DEAD_LETTER_QUEUE);
    }

    /**
     * US18-2。<strong>到着の見込みが届く</strong>。
     *
     * <p>届かなければ、荷主の画面には「未定」しか出ない。
     */
    @Test
    @DisplayName("経路のイベントが届くと、推定到着日を持つ")
    void keepsTheEstimatedArrivalWhenTheEventArrives() {
        startListening();
        String number = "TRK-20260822-9101";
        startTracking(number);
        assertThat(activities.findByTrackingNumber(TrackingNumber.of(number)).orElseThrow()
                .estimatedArrival())
                .as("経路が決まる前から日付が入っている")
                .isEmpty();

        publishRouted(number, "2027-09-15");

        awaitAssert(() -> assertThat(
                activities.findByTrackingNumber(TrackingNumber.of(number)).orElseThrow()
                        .estimatedArrival())
                .as("経路は決まったのに、推定到着日が届いていない")
                .contains(LocalDate.of(2027, Month.SEPTEMBER, 15)));
    }

    /**
     * <strong>知らない追跡番号では止まらない。</strong>
     *
     * <p>経路が決まるのと追跡が作られるのは別のイベントであり、届く順は入れ替わりうる。
     * ここで落とすと、原因が直るまで後続のイベントも進まなくなる。
     */
    @Test
    @DisplayName("知らない追跡番号でも、デッドレターへ回さない")
    void ignoresUnknownTrackingNumbers() {
        startListening();
        long before = deadLetterCount();

        publishRouted("TRK-20260822-9199", "2027-09-15");

        assertStaysTrue(() -> assertThat(deadLetterCount())
                .as("知らない追跡番号でデッドレターへ回った。後続のイベントまで止まる")
                .isEqualTo(before));
    }

    /**
     * <strong>読めない中身はデッドレターへ残る</strong>（[ADR-022] 決定 4）。
     *
     * <p>設定を書いたことと、落ちたイベントがそこへ届くことは別である。
     */
    @Test
    @DisplayName("読めない経路のイベントはデッドレターに残る")
    void movesUnprocessableEventsToTheDeadLetterQueue() {
        startListening();
        long before = deadLetterCount();

        // 日付として読めない値。**変換そのものが成り立たない入力**を送る
        publishRouted("TRK-20260822-9102", "きのう");

        awaitAssert(() -> assertThat(deadLetterCount())
                .as("処理できなかったイベントがどこにも残っていない")
                .isGreaterThan(before));
    }
}
