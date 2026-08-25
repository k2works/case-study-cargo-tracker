package com.example.trackingms.application.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.shared.domain.model.Location;
import com.example.trackingms.application.port.TrackingActivityRepository;
import com.example.trackingms.application.port.TrackingNotifier;
import com.example.trackingms.domain.model.ExceptionType;
import com.example.trackingms.domain.model.TrackingActivity;
import com.example.trackingms.domain.model.TrackingBookingId;
import com.example.trackingms.domain.model.TrackingEvent;
import com.example.trackingms.domain.model.TrackingExceptionEvent;
import com.example.trackingms.domain.model.TrackingNumber;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 未解決の例外がある貨物の並び順（IT9 返済枠 0.8・IT8 レビュー #17）。
 *
 * <p>並んでいない一覧は、20 件を超えると「上から順に見る」ことができなくなり、
 * 担当者は毎朝すべてを読み直すことになる。
 */
@DisplayName("未解決の例外がある貨物の一覧")
class OpenExceptionOrderTest {

    private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
    private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");
    private static final Instant OLD = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant RECENT = Instant.parse("2026-08-20T00:00:00Z");

    private List<TrackingActivity> found = List.of();

    private final TrackingActivityRepository activities = new TrackingActivityRepository() {
        @Override
        public TrackingActivity saveIfAbsent(TrackingActivity activity) {
            throw new UnsupportedOperationException("この検査では使わない");
        }

        @Override
        public void updateStatus(TrackingActivity activity) {
            throw new UnsupportedOperationException("この検査では使わない");
        }

        @Override
        public Optional<TrackingActivity> findByTrackingNumber(TrackingNumber trackingNumber) {
            return Optional.empty();
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
            return found;
        }

        @Override
        public List<TrackingExceptionEvent> findExceptions(TrackingNumber trackingNumber,
                int limit) {
            return List.of();
        }
    };

    private final ManageTrackingUseCase useCase = new ManageTrackingUseCase(activities,
            unLocode -> Optional.of(TOKYO), notifier(),
            // **テストで実時計を使わない。** 並び順の検査に「いま」は要らず、
            // 使うと実行時刻で結果が変わる余地を残す
            Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC));

    private static TrackingNotifier notifier() {
        return new TrackingNotifier() {
            @Override
            public void statusChanged(TrackingActivity activity) {
                // 並び順の検査では使わない
            }

            @Override
            public void exceptionRaised(TrackingActivity activity) {
                // 並び順の検査では使わない
            }

            @Override
            public void exceptionResolved(TrackingActivity activity) {
                // 並び順の検査では使わない
            }
        };
    }

    private static TrackingActivity with(String number, ExceptionType type, Instant occurredAt) {
        return TrackingActivity.start(TrackingNumber.of(number), TrackingBookingId.of("BKG-2026000001"),
                        TOKYO, LOS_ANGELES, LocalDate.of(2030, Month.SEPTEMBER, 20))
                .raiseException(type, "例外", occurredAt);
    }

    private List<String> orderedNumbers() {
        return useCase.withOpenExceptions().stream()
                .map(activity -> activity.trackingNumber().value())
                .toList();
    }

    /**
     * <strong>最も長く放置されているものが、最も危ない。</strong>
     *
     * <p>新しい順にすると、古い 1 件が下へ沈み続ける。
     */
    @Test
    @DisplayName("発生の古い順に並ぶ")
    void putsTheOldestFirst() {
        found = List.of(with("TRK-20260823-0002", ExceptionType.DELAY, RECENT),
                with("TRK-20260823-0001", ExceptionType.DELAY, OLD));

        assertThat(orderedNumbers())
                .containsExactly("TRK-20260823-0001", "TRK-20260823-0002");
    }

    /**
     * <strong>緊急は古さより先に立つ。</strong>
     *
     * <p>紛失は補償の話に直結する（[ADR-024] 決定 3）。古い遅延より先に手を打つ。
     */
    @Test
    @DisplayName("緊急な例外が、古い例外より先に並ぶ")
    void putsUrgentBeforeOlder() {
        found = List.of(with("TRK-20260823-0001", ExceptionType.DELAY, OLD),
                with("TRK-20260823-0002", ExceptionType.LOST, RECENT));

        assertThat(orderedNumbers())
                .containsExactly("TRK-20260823-0002", "TRK-20260823-0001");
    }

    /** 時刻の比較は業務のものであり、実行環境の既定時刻ではない。 */
    @Test
    @DisplayName("同じ緊急度なら、発生時刻だけで決まる")
    void comparesOnlyByOccurrence() {
        found = List.of(with("TRK-20260823-0002", ExceptionType.LOST,
                        RECENT.atZone(ZoneOffset.UTC).toInstant()),
                with("TRK-20260823-0001", ExceptionType.LOST, OLD));

        assertThat(orderedNumbers())
                .containsExactly("TRK-20260823-0001", "TRK-20260823-0002");
    }
}
