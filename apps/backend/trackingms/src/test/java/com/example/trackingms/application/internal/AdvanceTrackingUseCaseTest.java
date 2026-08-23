package com.example.trackingms.application.internal;

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
    };

    private final AdvanceTrackingUseCase advanceTracking = new AdvanceTrackingUseCase(activities);

    @Test
    @DisplayName("荷役が届くと、進んだ状態を書き込む")
    void writesTheAdvancedStatus() {
        advanceTracking.advance(NUMBER, "RECEIVE", "JPTYO");

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
        advanceTracking.advance(NUMBER, "RECEIVE", "JPTYO");
        written.clear();

        // 同じ荷役の再配送
        advanceTracking.advance(NUMBER, "RECEIVE", "JPTYO");

        assertThat(written).as("進んでいないのに書き込んだ").isEmpty();
    }

    @Test
    @DisplayName("知らない種別でも書き込まない")
    void doesNotWriteForUnknownHandling() {
        advanceTracking.advance(NUMBER, "CUSTOMS_INSPECTION", "JPTYO");

        assertThat(written).isEmpty();
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

        advanceTracking.advance(NUMBER, "CUSTOMS_INSPECTION", "JPTYO");
        advanceTracking.advance(NUMBER, "RECEIVE", "JPTYO");
        advanceTracking.advance(NUMBER, "RECEIVE", "JPTYO");

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
        assertThatCode(() -> advanceTracking.advance("TRK-99999999-9999", "RECEIVE", "JPTYO"))
                .doesNotThrowAnyException();

        assertThat(written).isEmpty();
    }
}
