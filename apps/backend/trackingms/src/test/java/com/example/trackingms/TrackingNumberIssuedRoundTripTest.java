package com.example.trackingms;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.trackingms.domain.model.TrackingActivity;
import com.example.trackingms.domain.model.TrackingBookingId;
import com.example.trackingms.domain.model.TrackingNumber;
import com.example.trackingms.domain.model.TrackingStatus;
import com.example.trackingms.infrastructure.messaging.TrackingEventChannels;
import java.time.LocalDate;
import java.time.Month;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 「追跡番号を発行した」の往復（[ADR-022]・成功基準 2・3）。
 *
 * <p>bookingms が送り、こちらが追跡を作る 1 本目の非同期連携である。
 */
@DisplayName("追跡番号の発行イベントの往復（実 RabbitMQ）")
class TrackingNumberIssuedRoundTripTest extends EventRoundTripTestBase {

    /** bookingms の型名。<strong>こちらのクラスパスには存在しない</strong>。 */
    private static final String PRODUCER_TYPE_ID =
            "com.example.bookingms.application.port.TrackingNumberIssued";

    private static String payload(String trackingNumber, String bookingId, String originUnLocode) {
        return """
                {"trackingNumber": "%s", "bookingId": "%s",
                 "originUnLocode": "%s", "destinationUnLocode": "USLAX",
                 "arrivalDeadline": "2030-09-20", "estimatedArrival": "2030-09-16",
                 "occurredAt": "2026-08-22T02:00:00Z"}
                """.formatted(trackingNumber, bookingId, originUnLocode);
    }

    private void publish(String json) {
        send(TrackingEventChannels.EXCHANGE, TrackingEventChannels.TRACKING_NUMBER_ISSUED,
                PRODUCER_TYPE_ID, json);
    }

    private long deadLetterCount() {
        return queueDepth(TrackingEventChannels.DEAD_LETTER_QUEUE);
    }

    /** 成功基準 2。 */
    @Test
    @DisplayName("発行されたイベントが届き、追跡の記録が残る")
    void startsTrackingWhenTheEventArrives() {
        startListening();
        String number = "TRK-20260822-9001";

        publish(payload(number, "BKG-2026000001", "JPTYO"));

        awaitAssert(() -> assertThat(activities.findByTrackingNumber(TrackingNumber.of(number)))
                .as("イベントは送ったのに追跡が作られていない")
                .isPresent());

        // 地点はこちらのマスタから引く（イベントが運ぶのは UN/LOCODE だけ）
        assertThat(activities.findByTrackingNumber(TrackingNumber.of(number)).orElseThrow())
                .satisfies(activity -> {
                    assertThat(activity.origin().name()).isEqualTo("Tokyo");
                    assertThat(activity.destination().name()).isEqualTo("Los Angeles");
                    assertThat(activity.trackingStatus()).isEqualTo(TrackingStatus.NOT_RECEIVED);
                    assertThat(activity.bookingId().value()).isEqualTo("BKG-2026000001");
                    // JSON をまたぐ型変換が起きるのはここだけ。NOT NULL 制約は「消える」を
                    // 捕まえるが、**1 日ずれる**ことは捕まえない
                    assertThat(activity.arrivalDeadline())
                            .isEqualTo(LocalDate.of(2030, Month.SEPTEMBER, 20));
                    // **到着の見込みは、追跡の作成と同じイベントで届く**（[ADR-024] 決定 4）。
                    // 別のイベントで送ると、2 つが別々のキューを通るため順序が保証されず、
                    // 先に届いた到着日は引く相手が無く捨てられる（kind で実際に起きた）
                    assertThat(activity.estimatedArrival())
                            .as("到着の見込みが届いていない。荷主の画面には「未定」しか出ない")
                            .contains(LocalDate.of(2030, Month.SEPTEMBER, 16));
                    // **到着期限とは別物である**
                    assertThat(activity.estimatedArrival())
                            .isNotEqualTo(java.util.Optional.of(activity.arrivalDeadline()));
                });
    }

    /** [ADR-022] 決定 5。再試行がある以上、二重配送は起こる。 */
    @Test
    @DisplayName("同じイベントが 2 回届いても追跡は 1 件")
    void isIdempotent() {
        startListening();
        String number = "TRK-20260822-9002";
        // 他のテストが残したぶんと混ざらないよう、増分で見る（実行順に依存させない）
        long deadLettersBefore = deadLetterCount();

        publish(payload(number, "BKG-2026000001", "JPTYO"));
        publish(payload(number, "BKG-2026000001", "JPTYO"));

        awaitAssert(() ->
                assertThat(activities.findByTrackingNumber(TrackingNumber.of(number))).isPresent());
        // 2 件目で一意制約に当たって落ちていれば、デッドレターに溜まる
        assertStaysTrue(() -> assertThat(deadLetterCount())
                .as("同じイベントの 2 回目で落ちている。冪等になっていない")
                .isEqualTo(deadLettersBefore));
    }

    /**
     * 成功基準 3。<strong>受け取れなかったイベントが消えない</strong>。
     *
     * <p>設定を書いたことと、落ちたイベントがそこへ届くことは別である。処理できない中身を
     * 送って、実際にデッドレターへ回ることを見る。
     */
    @Test
    @DisplayName("処理できなかったイベントはデッドレターに残る")
    void movesUnprocessableEventsToTheDeadLetterQueue() {
        startListening();
        long before = deadLetterCount();

        // 地点マスタに無い港。握りつぶすと、出発地の分からない追跡ができる
        publish(payload("TRK-20260822-9003", "BKG-2026000002", "XXXXX"));

        awaitAssert(() -> assertThat(deadLetterCount())
                .as("処理できなかったイベントがどこにも残っていない")
                .isGreaterThan(before));

        assertThat(activities.findByTrackingNumber(TrackingNumber.of("TRK-20260822-9003")))
                .as("処理できなかったのに追跡を作っている")
                .isEmpty();
    }

    /**
     * [ADR-022] 決定 5。<strong>二重に届いても、保存先の 1 回の書き込みで決まる</strong>。
     *
     * <p>上の {@link #isIdempotent()} は購読の入口から見ており、「探してから無ければ保存する」
     * 実装でも緑になる。ここは保存先を直接 2 回呼ぶ——事前の読み出しに頼っていると、
     * 2 回目が一意制約に当たって落ちる。
     */
    @Test
    @DisplayName("保存先を同じ追跡番号で 2 回呼んでも落ちず 1 件のまま")
    void saveIfAbsentDecidesByTheConstraint() {
        TrackingNumber number = TrackingNumber.of("TRK-20260822-9005");
        TrackingActivity activity = TrackingActivity.start(number,
                TrackingBookingId.of("BKG-2026000004"),
                locations.findByUnLocode("JPTYO").orElseThrow(),
                locations.findByUnLocode("USLAX").orElseThrow(),
                LocalDate.of(2030, Month.SEPTEMBER, 20));

        TrackingActivity first = activities.saveIfAbsent(activity);
        TrackingActivity second = activities.saveIfAbsent(activity);

        assertThat(second.id())
                .as("2 回目が別の行を作っている。冪等が保存先で決まっていない")
                .isEqualTo(first.id());
    }

    /**
     * [ADR-022] 決定 4。<strong>どのキューにも入らなかったイベントが消えない</strong>。
     *
     * <p>デッドレターが守るのは「受け取ったが処理できなかった」だけである。ルーティングキーの
     * 綴り違いや購読側の配線漏れでは、イベントはどのキューにも入らないまま消え、
     * <strong>発行側は成功を返す</strong>。交換機の予備の行き先に実際に届くことを見る。
     */
    @Test
    @DisplayName("どのキューにも結びつかないイベントは予備の行き先に残る")
    void keepsUnroutableEventsInTheAlternateExchange() {
        long before = unroutableCount();

        // 誰も結びつけていないルーティングキー（綴り違い・配線漏れと同じ形）
        send(TrackingEventChannels.EXCHANGE, "cargo.nobody-listens-to-this", PRODUCER_TYPE_ID,
                payload("TRK-20260822-9004", "BKG-2026000003", "JPTYO"));

        awaitAssert(() -> assertThat(unroutableCount())
                .as("行き場のないイベントがどこにも残っていない")
                .isGreaterThan(before));
    }
}
