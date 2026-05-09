package com.example.trackingms.application.internal.queryservices;

import com.example.trackingms.domain.exceptions.TrackingActivityNotFoundException;
import com.example.trackingms.domain.model.aggregates.TrackingActivity;
import com.example.trackingms.domain.model.valueobjects.TrackingBookingId;
import com.example.trackingms.domain.model.valueobjects.TrackingNumber;
import com.example.trackingms.domain.ports.TrackingActivityRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrackingQueryServiceTest {

    @Test
    void 有効な追跡番号でTrackingActivityを取得できる() {
        TrackingNumber number = new TrackingNumber("TRK-000001");
        TrackingActivity expected = new TrackingActivity(number, new TrackingBookingId("BK-001"));
        StubTrackingActivityRepository repository = new StubTrackingActivityRepository(expected);
        TrackingQueryService service = new TrackingQueryService(repository);

        TrackingActivity result = service.findByTrackingNumber("TRK-000001");

        assertThat(result).isSameAs(expected);
    }

    @Test
    void 存在しない追跡番号でTrackingActivityNotFoundExceptionがスローされる() {
        StubTrackingActivityRepository repository = new StubTrackingActivityRepository(null);
        TrackingQueryService service = new TrackingQueryService(repository);

        assertThatThrownBy(() -> service.findByTrackingNumber("TRK-999999"))
                .isInstanceOf(TrackingActivityNotFoundException.class)
                .hasMessageContaining("TRK-999999");
    }

    @Test
    void nullの追跡番号でNullPointerExceptionがスローされる() {
        StubTrackingActivityRepository repository = new StubTrackingActivityRepository(null);
        TrackingQueryService service = new TrackingQueryService(repository);

        assertThatThrownBy(() -> service.findByTrackingNumber(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void 空文字の追跡番号でIllegalArgumentExceptionがスローされる() {
        StubTrackingActivityRepository repository = new StubTrackingActivityRepository(null);
        TrackingQueryService service = new TrackingQueryService(repository);

        assertThatThrownBy(() -> service.findByTrackingNumber(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 不正フォーマットの追跡番号でIllegalArgumentExceptionがスローされる() {
        StubTrackingActivityRepository repository = new StubTrackingActivityRepository(null);
        TrackingQueryService service = new TrackingQueryService(repository);

        assertThatThrownBy(() -> service.findByTrackingNumber("INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * テスト用スタブリポジトリ
     */
    private static final class StubTrackingActivityRepository implements TrackingActivityRepository {
        private final TrackingActivity activity;

        private StubTrackingActivityRepository(TrackingActivity activity) {
            this.activity = activity;
        }

        @Override
        public TrackingActivity save(TrackingActivity activity) {
            return activity;
        }

        @Override
        public Optional<TrackingActivity> findByTrackingNumber(TrackingNumber trackingNumber) {
            if (activity != null && activity.getTrackingNumber().equals(trackingNumber)) {
                return Optional.of(activity);
            }
            return Optional.empty();
        }

        @Override
        public Optional<TrackingActivity> findByBookingId(TrackingBookingId bookingId) {
            return Optional.empty();
        }

        @Override
        public void update(TrackingActivity activity) {
            // no-op
        }

        @Override
        public long nextTrackingNumberSequence() {
            return 1L;
        }
    }
}
