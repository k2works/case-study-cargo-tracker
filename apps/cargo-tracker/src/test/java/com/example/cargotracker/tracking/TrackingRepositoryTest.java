package com.example.cargotracker.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.model.Location;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import com.example.cargotracker.tracking.domain.model.TrackingActivity;
import com.example.cargotracker.tracking.domain.model.TrackingActivityEvent;
import com.example.cargotracker.tracking.domain.model.TrackingBookingId;
import com.example.cargotracker.tracking.domain.model.TrackingEventType;
import com.example.cargotracker.tracking.domain.model.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.TrackingVoyageNumber;
import com.example.cargotracker.tracking.domain.model.TransportStatus;
import com.example.cargotracker.tracking.domain.repository.TrackingActivityRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 追跡と荷役の永続化（US14 / US15）。
 *
 * <p>SQL の正しさは実 PostgreSQL で確かめる（ADR-003）。
 */
class TrackingRepositoryTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private TrackingActivityRepository trackingRepository;

    private static int sequence;

    private static TrackingNumber 追跡番号() {
        return new TrackingNumber("TRK-20261101-%04d".formatted(++sequence));
    }

    private static TrackingActivityEvent イベント(
            TrackingEventType type, String unlocode, String at, String voyage) {
        return TrackingActivityEvent.fromHandling(
                type, Instant.parse(at), Location.of(unlocode),
                voyage == null ? null : new TrackingVoyageNumber(voyage));
    }

    /** 追跡レコードを保存して読み戻せる。 */
    @Test
    void 追跡レコードを往復できる() {
        var number = 追跡番号();
        var bookingId = new TrackingBookingId(UUID.randomUUID());
        trackingRepository.save(TrackingActivity.issue(number, bookingId, null, null));

        var loaded = trackingRepository.findByTrackingNumber(number).orElseThrow();

        assertThat(loaded.transportStatus()).isEqualTo(TransportStatus.NOT_RECEIVED);
        assertThat(loaded.bookingId().value()).isEqualTo(bookingId.value());
        assertThat(loaded.events()).isEmpty();
    }

    /**
     * <strong>輸送状態とイベントを 1 つの操作として書く。</strong>
     *
     * <p>片方だけ残ると「積込済なのにイベントが無い」状態になる。
     */
    @Test
    void 輸送状態とイベントを往復できる() {
        var number = 追跡番号();
        trackingRepository.save(
                TrackingActivity.issue(number, new TrackingBookingId(UUID.randomUUID()), null, null));
        var tracking = trackingRepository.findByTrackingNumber(number).orElseThrow();
        tracking.recordEvent(イベント(TrackingEventType.RECEIVE, "JPOSA", "2026-11-01T01:00:00Z", null));
        tracking.recordEvent(イベント(TrackingEventType.LOAD, "JPOSA", "2026-11-02T01:00:00Z", "V001"));

        assertThat(trackingRepository.update(tracking)).isTrue();

        var loaded = trackingRepository.findByTrackingNumber(number).orElseThrow();
        assertThat(loaded.transportStatus()).isEqualTo(TransportStatus.LOADED);
        assertThat(loaded.events())
                .extracting(TrackingActivityEvent::type)
                .containsExactly(TrackingEventType.RECEIVE, TrackingEventType.LOAD);
        assertThat(loaded.latestEvent().voyageNumber().value()).isEqualTo("V001");
    }

    /**
     * <strong>イベントは発生日時の順に読み戻す。</strong>
     *
     * <p>順序が崩れると、タイムラインが実際の輸送の順序と食い違う。
     */
    @Test
    void イベントは発生日時の順に読み戻される() {
        var number = 追跡番号();
        trackingRepository.save(
                TrackingActivity.issue(number, new TrackingBookingId(UUID.randomUUID()), null, null));
        var tracking = trackingRepository.findByTrackingNumber(number).orElseThrow();
        // 後から入力した受領のほうが、発生は早い
        tracking.recordEvent(イベント(TrackingEventType.LOAD, "JPOSA", "2026-11-02T01:00:00Z", "V001"));
        tracking.recordEvent(イベント(TrackingEventType.RECEIVE, "JPOSA", "2026-11-01T01:00:00Z", null));
        assertThat(trackingRepository.update(tracking)).isTrue();

        assertThat(trackingRepository.findByTrackingNumber(number).orElseThrow().events())
                .extracting(TrackingActivityEvent::type)
                .containsExactly(TrackingEventType.RECEIVE, TrackingEventType.LOAD);
    }

    /** 同時更新の後勝ちを防ぐ（楽観的ロック）。 */
    @Test
    void 同時に荷役を登録すると後の保存が拒否される() {
        var number = 追跡番号();
        trackingRepository.save(
                TrackingActivity.issue(number, new TrackingBookingId(UUID.randomUUID()), null, null));
        var first = trackingRepository.findByTrackingNumber(number).orElseThrow();
        var second = trackingRepository.findByTrackingNumber(number).orElseThrow();

        first.recordEvent(イベント(TrackingEventType.RECEIVE, "JPOSA", "2026-11-01T01:00:00Z", null));
        second.recordEvent(イベント(TrackingEventType.LOAD, "JPOSA", "2026-11-02T01:00:00Z", "V001"));

        assertThat(trackingRepository.update(first)).isTrue();
        assertThat(trackingRepository.update(second)).isFalse();
    }

    /** 予約 ID からも引き当てられる（予約詳細に追跡の状態を出すため）。 */
    @Test
    void 予約IDから追跡レコードを引き当てられる() {
        var number = 追跡番号();
        var bookingId = new TrackingBookingId(UUID.randomUUID());
        trackingRepository.save(TrackingActivity.issue(number, bookingId, null, null));

        assertThat(trackingRepository.findByBookingId(bookingId))
                .get()
                .extracting(t -> t.trackingNumber().value())
                .isEqualTo(number.value());
    }
}
