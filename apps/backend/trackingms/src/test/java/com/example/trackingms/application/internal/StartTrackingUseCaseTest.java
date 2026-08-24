package com.example.trackingms.application.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.shared.domain.model.Location;
import com.example.trackingms.application.port.LocationRepository;
import com.example.trackingms.application.port.TrackingActivityRepository;
import com.example.trackingms.domain.model.TrackingActivity;
import com.example.trackingms.domain.model.TrackingEvent;
import com.example.trackingms.domain.model.TrackingExceptionEvent;
import com.example.trackingms.domain.model.TrackingNumber;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 追跡を始める（US14-3）。
 *
 * <p>ここで確かめるのは<strong>2 回目のイベントで何が起きるか</strong>である。
 * 追跡の作成は冪等でよいが、<strong>推定到着日まで冪等にしてはいけない</strong>。
 * 経路を組み直した貨物は新しい見込みを持って届くのに、作成済みだからと丸ごと
 * 捨てると、荷主は古い到着日を見続ける（IT9 返済枠 0.5・IT8 レビュー #15）。
 */
@DisplayName("追跡を始める")
class StartTrackingUseCaseTest {

    private static final String NUMBER = "TRK-20260823-0001";
    private static final String BOOKING = "BKG-2026000001";
    private static final LocalDate DEADLINE = LocalDate.of(2030, Month.SEPTEMBER, 20);
    private static final LocalDate FIRST_ESTIMATE = LocalDate.of(2030, Month.SEPTEMBER, 15);
    private static final LocalDate REVISED_ESTIMATE = LocalDate.of(2030, Month.SEPTEMBER, 18);

    private TrackingActivity stored;

    /** 更新の書き込み回数。**要らない更新をしていない**ことも見る。 */
    private final List<LocalDate> updates = new ArrayList<>();

    private final TrackingActivityRepository activities = new TrackingActivityRepository() {
        @Override
        public TrackingActivity saveIfAbsent(TrackingActivity activity) {
            if (stored == null) {
                stored = activity;
            }
            return stored;
        }

        @Override
        public void updateStatus(TrackingActivity activity) {
            updates.add(activity.estimatedArrival().orElse(null));
            stored = activity;
        }

        @Override
        public Optional<TrackingActivity> findByTrackingNumber(TrackingNumber trackingNumber) {
            return Optional.ofNullable(stored);
        }

        @Override
        public void appendEvent(TrackingNumber trackingNumber, TrackingEvent event) {
            throw new UnsupportedOperationException("この検査では使わない");
        }

        @Override
        public List<TrackingEvent> findEvents(TrackingNumber trackingNumber, int limit) {
            return List.of();
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
        public List<TrackingExceptionEvent> findExceptions(TrackingNumber trackingNumber,
                int limit) {
            throw new UnsupportedOperationException("この検査では使わない");
        }
    };

    private final LocationRepository locations =
            unLocode -> Optional.of(Location.of(unLocode, unLocode));

    private final StartTrackingUseCase useCase = new StartTrackingUseCase(activities, locations);

    private TrackingActivity start(LocalDate estimatedArrival) {
        return useCase.start(NUMBER, BOOKING, "JPTYO", "USLAX", DEADLINE, estimatedArrival);
    }

    @Test
    @DisplayName("初回は追跡を作り、推定到着日を持つ")
    void createsTheTrackingOnTheFirstEvent() {
        TrackingActivity started = start(FIRST_ESTIMATE);

        assertThat(started.trackingNumber().value()).isEqualTo(NUMBER);
        assertThat(started.estimatedArrival()).contains(FIRST_ESTIMATE);
    }

    /**
     * <strong>経路を組み直した貨物は、新しい見込みで届く。</strong>
     *
     * <p>作成済みだからとイベントを丸ごと捨てると、荷主は古い到着日を見続ける。
     * 追跡そのものは作り直さない——作り直すと、これまでの経過が消える。
     */
    @Test
    @DisplayName("2 回目のイベントで推定到着日が新しくなる")
    void updatesTheEstimateOnALaterEvent() {
        start(FIRST_ESTIMATE);

        TrackingActivity again = start(REVISED_ESTIMATE);

        assertThat(again.estimatedArrival()).contains(REVISED_ESTIMATE);
        assertThat(updates).containsExactly(REVISED_ESTIMATE);
    }

    /**
     * <strong>同じイベントの再送では書き込まない。</strong>
     *
     * <p>再試行がある以上、同じ中身が 2 回届くのは普通のことである。毎回書くと、
     * 何も変わっていない更新が記録に積まれ、変化を追う手がかりが薄まる。
     */
    @Test
    @DisplayName("同じ推定到着日の再送では書き込まない")
    void doesNotWriteWhenNothingChanged() {
        start(FIRST_ESTIMATE);

        start(FIRST_ESTIMATE);

        assertThat(updates).isEmpty();
    }

    /**
     * <strong>空の再送で消さない。</strong>
     *
     * <p>推定到着日を運ばないイベント（経路が決まる前の再送）で上書きすると、
     * いったん出せていた見込みが「未定」に戻る。
     */
    @Test
    @DisplayName("推定到着日を持たないイベントでは、いまの値を消さない")
    void keepsTheEstimateWhenTheEventCarriesNone() {
        start(FIRST_ESTIMATE);

        TrackingActivity again = start(null);

        assertThat(again.estimatedArrival()).contains(FIRST_ESTIMATE);
        assertThat(updates).isEmpty();
    }
}
