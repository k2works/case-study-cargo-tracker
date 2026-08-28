package com.example.trackingms.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.shared.domain.model.Location;
import com.example.trackingms.application.port.TrackingActivityRepository;
import com.example.trackingms.application.port.TrackingNoticeRepository;
import com.example.trackingms.domain.model.TrackingActivity;
import com.example.trackingms.domain.model.TrackingBookingId;
import com.example.trackingms.domain.model.TrackingEvent;
import com.example.trackingms.domain.model.TrackingExceptionEvent;
import com.example.trackingms.domain.model.TrackingNotice;
import com.example.trackingms.domain.model.TrackingNumber;
import com.example.trackingms.domain.model.TrackingStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * キャンセルが確定したことを、荷主のお知らせに残す（US30-6・[ADR-025] 決定 3）。
 *
 * <p><strong>公開追跡が開いているから知らせる。</strong>知らせないと、荷主は自分が
 * 申し入れて承認されたキャンセルを、画面で「輸送中」と否定される。
 */
@DisplayName("キャンセルをお知らせに残す")
class NoteCancellationUseCaseTest {

    private static final String NUMBER = "TRK-20260823-0001";
    private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
    private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");

    private final Clock clock =
            Clock.fixed(Instant.parse("2026-09-06T00:00:00Z"), ZoneId.of("Asia/Tokyo"));

    private final List<TrackingNotice> notes = new ArrayList<>();

    private final TrackingActivity stored = TrackingActivity.start(TrackingNumber.of(NUMBER),
            TrackingBookingId.of("BKG-2026000001"), TOKYO, LOS_ANGELES,
            LocalDate.of(2030, Month.SEPTEMBER, 20));

    private final NoteCancellationUseCase useCase =
            new NoteCancellationUseCase(new StubActivities(), new StubNotices(), clock);

    @Test
    @DisplayName("お知らせに残る")
    void notesTheCancellation() {
        useCase.note(NUMBER);

        assertThat(notes).hasSize(1);
        assertThat(notes.getFirst().message()).isEqualTo(NoteCancellationUseCase.MESSAGE);
    }

    /**
     * <strong>社内の手がかりを書かない。</strong>
     *
     * <p>この文言は認証の外にある画面へ出る。誰の判断で止まったか・どこで降ろすかは
     * 書かない——荷主が知りたいのは「自分の申し入れが通ったか」である。
     */
    @Test
    @DisplayName("お知らせに社内の手がかりを書かない")
    void doesNotLeakInternalDetails() {
        useCase.note(NUMBER);

        assertThat(notes.getFirst().message())
                .doesNotContain("tracker")
                .doesNotContain("sales")
                .doesNotContain("BKG-")
                .doesNotContain("陸揚げ");
    }

    /** <strong>冪等である。</strong>2 通目が残ると、荷主の画面に同じ文が 2 行並ぶ。 */
    @Test
    @DisplayName("同じイベントが 2 回届いても、お知らせは 1 件のまま")
    void isIdempotent() {
        useCase.note(NUMBER);
        useCase.note(NUMBER);

        assertThat(notes).hasSize(1);
    }

    /** 知らない追跡番号では止まらない。例外にすると後続のイベントも処理されなくなる。 */
    @Test
    @DisplayName("知らない追跡番号では止まらない")
    void doesNotFailForAnUnknownTrackingNumber() {
        assertThatCode(() -> useCase.note("TRK-20260823-9999")).doesNotThrowAnyException();
        assertThat(notes).isEmpty();
    }

    /**
     * <strong>{@code TrackingStatus} に値を足さない</strong>（[ADR-025] 決定 3）。
     *
     * <p>{@code CANCELLED} を足すと、進行の並び（{@code canAdvanceTo} の判定）に
     * 「進まない値」がもう 1 つ増える。IT8 で {@code EXCEPTION} / {@code UNKNOWN} の
     * 2 値が並び順の外にあることが実バグを生んだばかりである。
     */
    @Test
    @DisplayName("追跡の状態に、キャンセルを足さない")
    void doesNotIntroduceACancelledStatus() {
        // **先に空でないことを確かめる。** 列挙が読めていなければ doesNotContain は
        // 常に真になり、値を足しても緑のままになる（検査が何も守らない）
        assertThat(Arrays.stream(TrackingStatus.values()).map(Enum::name))
                .as("追跡の状態が 1 つも読めていない。検査が何も守らないまま緑になる")
                .isNotEmpty()
                .as("状態を足している。進行の並びに「進まない値」が増える")
                .doesNotContain("CANCELLED");
    }

    private final class StubActivities implements TrackingActivityRepository {

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
            return NUMBER.equals(trackingNumber.value()) ? Optional.of(stored) : Optional.empty();
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
    }

    private final class StubNotices implements TrackingNoticeRepository {

        @Override
        public void save(TrackingNumber trackingNumber, TrackingNotice notice) {
            notes.add(notice);
        }

        @Override
        public List<TrackingNotice> findByTrackingNumber(TrackingNumber trackingNumber,
                int limit) {
            return List.copyOf(notes);
        }
    }
}
