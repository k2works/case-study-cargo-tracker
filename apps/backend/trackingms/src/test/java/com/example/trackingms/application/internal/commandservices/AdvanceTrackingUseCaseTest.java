package com.example.trackingms.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.shared.domain.model.Location;
import com.example.trackingms.application.port.TrackingActivityRepository;
import com.example.trackingms.domain.model.TrackingActivity;
import com.example.trackingms.domain.model.TrackingBookingId;
import com.example.trackingms.domain.model.TrackingNumber;
import com.example.trackingms.domain.model.TrackingStatus;
import java.time.LocalDate;
import java.time.Month;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * 荷役の記録に応じて追跡を進める（US15-4・[ADR-023] 決定 5）。
 *
 * <p>ここで確かめるのは<strong>いつ書き込むか</strong>である。状態の遷移そのものは
 * 集約と列挙が持つ。
 */
@DisplayName("追跡を進める")
class AdvanceTrackingUseCaseTest {

    private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
    private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");
    private static final String NUMBER = "TRK-20260823-0001";

    /** 書き込まれた状態。何回書いたかを数えるために持つ。 */
    private final List<TrackingStatus> written = new ArrayList<>();

    /** 積まれた出来事。**状態が動いたのに経過に出ない**形を捕まえるために持つ。 */
    private final List<com.example.trackingms.domain.model.TrackingEvent> appendedEvents =
            new ArrayList<>();

    private TrackingActivity stored = TrackingActivity.start(TrackingNumber.of(NUMBER),
            TrackingBookingId.of("BKG-2026000001"), TOKYO, LOS_ANGELES,
            LocalDate.of(2030, Month.SEPTEMBER, 20));

    private final TrackingActivityRepository activities = new TrackingActivityRepository() {
        @Override
        public TrackingActivity saveIfAbsent(TrackingActivity activity) {
            throw new UnsupportedOperationException("この検査では使わない");
        }

        @Override
        public void updateStatus(TrackingActivity activity) {
            written.add(activity.trackingStatus());
            stored = activity;
        }

        @Override
        public Optional<TrackingActivity> findByTrackingNumber(TrackingNumber trackingNumber) {
            return NUMBER.equals(trackingNumber.value()) ? Optional.of(stored) : Optional.empty();
        }

        @Override
        public void appendEvent(TrackingNumber trackingNumber,
                com.example.trackingms.domain.model.TrackingEvent event) {
            appendedEvents.add(event);
        }

        @Override
        public List<com.example.trackingms.domain.model.TrackingEvent> findEvents(
                TrackingNumber trackingNumber, int limit) {
            return List.copyOf(appendedEvents);
        }

        @Override
        public void saveException(TrackingNumber trackingNumber, TrackingActivity activity) {
            throw new UnsupportedOperationException("この検査では使わない");
        }

        @Override
        public List<TrackingActivity> findWithOpenExceptions(int limit) {
            throw new UnsupportedOperationException("この検査では使わない");
        }

        @Override
        public List<com.example.trackingms.domain.model.TrackingExceptionEvent> findExceptions(
                TrackingNumber trackingNumber, int limit) {
            throw new UnsupportedOperationException("この検査では使わない");
        }
    };

    /** 地点マスタ。名前が引けないことで記録を止めないことも、ここで確かめる。 */
    private final com.example.trackingms.application.port.LocationRepository locations =
            unLocode -> Optional.of(Location.of(unLocode, "Tokyo"));

    /** 通知したという事実。**メールは送らない**（[ADR-024] 決定 9）。 */
    private final List<String> notified = new ArrayList<>();

    private final com.example.trackingms.application.port.TrackingNotifier notifier =
            new com.example.trackingms.application.port.TrackingNotifier() {
                @Override
                public void statusChanged(TrackingActivity activity) {
                    notified.add(activity.trackingStatus().name());
                }

                @Override
                public void exceptionRaised(TrackingActivity activity) {
                    throw new UnsupportedOperationException("この検査では使わない");
                }

                @Override
                public void exceptionResolved(TrackingActivity activity) {
                    throw new UnsupportedOperationException("この検査では使わない");
                }
            };

    private final AdvanceTrackingUseCase advanceTracking =
            new AdvanceTrackingUseCase(activities, locations, notifier);

    /** 荷役の作業日時。**経過にはこれが残る**——受け取った時刻ではない。 */
    private static final java.time.Instant COMPLETED_AT =
            java.time.Instant.parse("2026-08-23T02:00:00Z");

    @Test
    @DisplayName("荷役が届くと、進んだ状態を書き込む")
    void writesTheAdvancedStatus() {
        advanceTracking.advance(NUMBER, "RECEIVE", "JPTYO", COMPLETED_AT);

        assertThat(written).containsExactly(TrackingStatus.RECEIVED);
    }

    /**
     * <strong>同じ内容の更新で行を触らない。</strong>
     *
     * <p>無条件に書くと、再配送のたびに {@code updated_at} が動き、「いつ状態が変わったか」が
     * 読めなくなる。
     */
    @Test
    @DisplayName("進まない荷役では書き込まない")
    void doesNotWriteWhenNothingAdvances() {
        advanceTracking.advance(NUMBER, "RECEIVE", "JPTYO", COMPLETED_AT);
        written.clear();

        // 同じ荷役の再配送
        advanceTracking.advance(NUMBER, "RECEIVE", "JPTYO", COMPLETED_AT);

        assertThat(written).as("進んでいないのに書き込んだ").isEmpty();
    }

    @Test
    @DisplayName("知らない種別でも書き込まない")
    void doesNotWriteForUnknownHandling() {
        advanceTracking.advance(NUMBER, "CUSTOMS_INSPECTION", "JPTYO", COMPLETED_AT);

        assertThat(written).isEmpty();
    }

    /**
     * US18-3。<strong>状態が動いたら、経過にも残る</strong>。
     *
     * <p>状態は動いたのに経過に出ない行ができると、荷主は「いつ変わったか」を読めない。
     * IT7 の購読経路は状態だけを動かしていた。
     */
    @Test
    @DisplayName("荷役で進んだら、経過にも積む")
    void appendsAnEventWhenItAdvances() {
        advanceTracking.advance(NUMBER, "RECEIVE", "JPTYO", COMPLETED_AT);

        assertThat(appendedEvents)
                .as("状態は動いたのに、経過に出ていない")
                .hasSize(1)
                .allSatisfy(event -> {
                    assertThat(event.trackingStatus()).isEqualTo(TrackingStatus.RECEIVED);
                    // **受け取った時刻ではなく、実際に作業した時刻を残す**
                    assertThat(event.occurredAt()).isEqualTo(COMPLETED_AT);
                    assertThat(event.source().name()).isEqualTo("HANDLING");
                });
    }

    /** 進まなければ、経過にも積まない。同じ荷役の再配送で行が増えない。 */
    @Test
    @DisplayName("進まない荷役では、経過にも積まない")
    void doesNotAppendWhenNothingAdvances() {
        advanceTracking.advance(NUMBER, "RECEIVE", "JPTYO", COMPLETED_AT);
        appendedEvents.clear();

        advanceTracking.advance(NUMBER, "RECEIVE", "JPTYO", COMPLETED_AT);

        assertThat(appendedEvents).as("進んでいないのに経過へ積んだ").isEmpty();
    }

    /**
     * [ADR-024] 決定 9。<strong>状態が変わったら荷主へ知らせる——ただしメールは送らない</strong>。
     */
    @Test
    @DisplayName("荷役で進んだら、通知した事実を残す")
    void recordsANoticeWhenItAdvances() {
        advanceTracking.advance(NUMBER, "RECEIVE", "JPTYO", COMPLETED_AT);

        assertThat(notified).containsExactly("RECEIVED");
    }

    /**
     * <strong>例外にしないことは、記録しないことではない。</strong>
     *
     * <p>知らない種別と、進まない種別は、どちらも「書き込まない」に落ちる。そこだけを
     * 見ていると、相手が新しい種別を送り始めたことに誰も気づかない。契約の食い違いは
     * 残す——ただしデッドレターへは回さない（回すと種別 1 つで後続の荷役が止まる）。
     */
    @Test
    @DisplayName("知らない種別は、進まない種別と区別して記録される")
    void recordsUnknownHandlingType() {
        List<String> recorded = capturedWarnings();

        advanceTracking.advance(NUMBER, "CUSTOMS_INSPECTION", "JPTYO", COMPLETED_AT);
        advanceTracking.advance(NUMBER, "RECEIVE", "JPTYO", COMPLETED_AT);
        advanceTracking.advance(NUMBER, "RECEIVE", "JPTYO", COMPLETED_AT);

        assertThat(recorded)
                .as("知らない種別が記録されないか、進まない種別まで記録された")
                .hasSize(1);
        assertThat(recorded.get(0)).contains("CUSTOMS_INSPECTION").contains(NUMBER);
    }

    /** 使用中のログ実装から警告を拾う。記録されたことを、実際に残る場所で確かめる。 */
    private static List<String> capturedWarnings() {
        Logger logger = (Logger) LoggerFactory.getLogger(AdvanceTrackingUseCase.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return new AbstractList<>() {
            @Override
            public String get(int index) {
                return appender.list.get(index).getFormattedMessage();
            }

            @Override
            public int size() {
                return (int) appender.list.stream()
                        .filter(event -> event.getLevel() == Level.WARN).count();
            }
        };
    }

    /**
     * <strong>知らない追跡番号では止まらない。</strong>
     *
     * <p>例外にするとイベントがデッドレターへ回り、原因が直るまで後続の荷役も進まなくなる。
     * 取りこぼしは運用の照会が拾う。
     */
    @Test
    @DisplayName("知らない追跡番号では、例外にせず何もしない")
    void ignoresUnknownTrackingNumber() {
        assertThatCode(() ->
                advanceTracking.advance("TRK-99999999-9999", "RECEIVE", "JPTYO", COMPLETED_AT))
                .doesNotThrowAnyException();

        assertThat(written).isEmpty();
    }
}
