package com.example.trackingms.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.shared.domain.model.Location;
import com.example.trackingms.domain.repository.TrackingActivityRepository;
import com.example.trackingms.application.internal.outboundservices.acl.TrackingNotifier;
import com.example.trackingms.domain.model.valueobjects.ExceptionType;
import com.example.trackingms.domain.model.aggregates.TrackingActivity;
import com.example.trackingms.domain.model.valueobjects.TrackingBookingId;
import com.example.trackingms.domain.model.valueobjects.TrackingEvent;
import com.example.trackingms.domain.model.entities.TrackingExceptionEvent;
import com.example.trackingms.domain.model.valueobjects.TrackingNumber;
import com.example.trackingms.domain.model.valueobjects.TrackingStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 予定ルート外の荷役を例外として自動で起票する（US28-2）。
 *
 * <p><strong>誤配は発見が遅れるほど被害が膨らむ。</strong>貨物は目的地から遠ざかり続け、
 * 納期遅延と輸送コストが積み上がる。追跡管理者の未解決一覧に現れて初めて、
 * 経路の組み直しが始まる。
 */
@DisplayName("誤配の自動起票")
class DetectMisrouteUseCaseTest {

    private static final String NUMBER = "TRK-20260823-0001";
    private static final Instant AT = Instant.parse("2027-09-05T00:00:00Z");
    private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
    private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");

    private TrackingActivity stored = TrackingActivity.start(TrackingNumber.of(NUMBER),
            TrackingBookingId.of("BKG-2026000001"), TOKYO, LOS_ANGELES,
            LocalDate.of(2030, Month.SEPTEMBER, 20));

    private final List<TrackingActivity> savedExceptions = new ArrayList<>();
    private final List<TrackingEvent> appended = new ArrayList<>();
    private final List<String> notified = new ArrayList<>();

    private final DetectMisrouteUseCase useCase =
            new DetectMisrouteUseCase(new StubActivities(), new StubNotifier());

    @Test
    @DisplayName("予定ルート外の荷役で、誤配の例外が起票される")
    void raisesMisrouteOnOffRouteHandling() {
        useCase.onHandlingActivityRegistered(NUMBER, "SGSIN", AT, true);

        assertThat(stored.activeException()).isPresent();
        assertThat(stored.activeException().orElseThrow().exceptionType())
                .isEqualTo(ExceptionType.MISROUTE);
        assertThat(stored.trackingStatus()).isEqualTo(TrackingStatus.EXCEPTION);
        assertThat(savedExceptions).hasSize(1);
        assertThat(appended).hasSize(1);
        assertThat(notified).hasSize(1);
    }

    /**
     * <strong>どこで外れたかを載せる。</strong>
     *
     * <p>この文言を読むのは追跡管理者であり、経路設計者へ渡すときの手がかりになる。
     * 「誤配が起きました」だけでは、受け取った人は場所を別に探すことになる。
     */
    @Test
    @DisplayName("発生状況に、外れた場所が入る")
    void carriesTheLocationIntoTheDescription() {
        useCase.onHandlingActivityRegistered(NUMBER, "SGSIN", AT, true);

        assertThat(stored.activeException().orElseThrow().description())
                .as("外れた場所が伝わらない。受け取った人は場所を別に探すことになる")
                .contains("SGSIN");
    }

    /** <strong>予定どおりの荷役では何もしない。</strong>一覧が誤配で埋まると読まれなくなる。 */
    @Test
    @DisplayName("予定どおりの荷役では起票しない")
    void ignoresPlannedHandling() {
        useCase.onHandlingActivityRegistered(NUMBER, "JPTYO", AT, false);

        assertThat(stored.activeException()).isEmpty();
        assertThat(savedExceptions).isEmpty();
        assertThat(notified).isEmpty();
    }

    /**
     * <strong>未解決の例外があるときは起票しない</strong>（[ADR-024] 決定 2）。
     *
     * <p>2 件目を許すと、発生前の状態が上書きされて解決しても戻れない。
     */
    @Test
    @DisplayName("未解決の例外があるときは、重ねて起票しない")
    void doesNotStackOnAnUnresolvedException() {
        stored = stored.detectException(ExceptionType.CUSTOMS_HOLD, "税関で留置", AT);
        savedExceptions.clear();
        notified.clear();

        useCase.onHandlingActivityRegistered(NUMBER, "SGSIN", AT, true);

        assertThat(stored.activeException().orElseThrow().exceptionType())
                .as("先に起きていた例外が上書きされている。解決しても戻る先が失われる")
                .isEqualTo(ExceptionType.CUSTOMS_HOLD);
        assertThat(savedExceptions).isEmpty();
    }

    /**
     * <strong>知らない追跡番号では止まらない。</strong>
     *
     * <p>例外にすると、後続のイベントも処理されなくなる。
     */
    @Test
    @DisplayName("知らない追跡番号では止まらない")
    void doesNotFailForAnUnknownTrackingNumber() {
        useCase.onHandlingActivityRegistered("TRK-20260823-9999", "SGSIN", AT, true);

        assertThat(savedExceptions).isEmpty();
    }

    /**
     * <strong>手で起票する入口には載せない</strong>（[ADR-024] 決定 11）。
     *
     * <p>仕組みが検知する種別を人が起票できると、**何が起きたのかを人が決める**ことになる。
     */
    @Test
    @DisplayName("誤配は、人が起票する入口には出ない")
    void keepsMisrouteOutOfTheOperatorEntry() {
        assertThat(ExceptionType.MISROUTE.raisableByOperator())
                .as("誤配が手動起票の選択肢に出ている。仕組みが検知する種別である")
                .isFalse();
    }

    private final class StubActivities implements TrackingActivityRepository {

        @Override
        public TrackingActivity saveIfAbsent(TrackingActivity activity) {
            throw new UnsupportedOperationException("この検査では使わない");
        }

        @Override
        public void updateStatus(TrackingActivity activity) {
            stored = activity;
        }

        @Override
        public Optional<TrackingActivity> findByTrackingNumber(TrackingNumber trackingNumber) {
            return NUMBER.equals(trackingNumber.value()) ? Optional.of(stored) : Optional.empty();
        }

        @Override
        public void appendEvent(TrackingNumber trackingNumber, TrackingEvent event) {
            appended.add(event);
        }

        @Override
        public List<TrackingEvent> findEvents(TrackingNumber trackingNumber, int limit) {
            return List.copyOf(appended);
        }

        @Override
        public void saveException(TrackingNumber trackingNumber, TrackingActivity activity) {
            savedExceptions.add(activity);
        }

        @Override
        public List<TrackingActivity> findWithOpenExceptions(int limit) {
            throw new UnsupportedOperationException("この検査では使わない");
        }

        @Override
        public List<TrackingExceptionEvent> findExceptions(TrackingNumber trackingNumber,
                int limit) {
            return List.of();
        }
    }

    private final class StubNotifier implements TrackingNotifier {

        @Override
        public void statusChanged(TrackingActivity activity) {
            throw new UnsupportedOperationException("この検査では使わない");
        }

        @Override
        public void exceptionRaised(TrackingActivity activity) {
            notified.add(activity.trackingNumber().value());
        }

        @Override
        public void exceptionResolved(TrackingActivity activity) {
            throw new UnsupportedOperationException("この検査では使わない");
        }
    }
}
