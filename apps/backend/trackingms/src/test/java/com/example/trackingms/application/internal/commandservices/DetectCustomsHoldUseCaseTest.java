package com.example.trackingms.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * 通関の留置を例外として自動で起票する（US29-5）。
 *
 * <p><strong>留め置かれたことが誰の目にも入らないと、貨物はそのまま止まる。</strong>
 * 追跡管理者の未解決一覧に現れて初めて、税関への問い合わせが始まる。
 */
@DisplayName("税関保留の自動起票")
class DetectCustomsHoldUseCaseTest {

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

    private final DetectCustomsHoldUseCase useCase =
            new DetectCustomsHoldUseCase(new StubActivities(), new StubNotifier());

    @Test
    @DisplayName("留置になると、税関保留の例外が起票される")
    void raisesCustomsHoldOnHeld() {
        useCase.onCustomsStatusChanged(NUMBER, "HELD", "書類不備", AT);

        assertThat(stored.activeException()).isPresent();
        assertThat(stored.activeException().orElseThrow().exceptionType())
                .isEqualTo(ExceptionType.CUSTOMS_HOLD);
        assertThat(stored.trackingStatus()).isEqualTo(TrackingStatus.EXCEPTION);
        assertThat(savedExceptions).hasSize(1);
        assertThat(appended).hasSize(1);
        assertThat(notified).hasSize(1);
    }

    /** 理由は追跡管理者が税関に問い合わせるときの手がかりになる。 */
    @Test
    @DisplayName("発生状況に、通関の理由が残る")
    void keepsTheCustomsReason() {
        useCase.onCustomsStatusChanged(NUMBER, "HELD", "書類不備", AT);

        assertThat(stored.activeException().orElseThrow().description())
                .contains("書類不備");
    }

    /**
     * <strong>留置以外では何もしない。</strong>
     *
     * <p>通関済は引取のガードが通る合図であり、例外ではない。審査中・不可も
     * 追跡管理者が直ちに動く事象ではない。
     */
    @Test
    @DisplayName("留置以外の通関状態では、例外を起票しない")
    void ignoresOtherStatuses() {
        for (String status : List.of("PENDING", "CLEARED", "REJECTED")) {
            useCase.onCustomsStatusChanged(NUMBER, status, "理由", AT);
        }

        assertThat(stored.activeException()).isEmpty();
        assertThat(savedExceptions).isEmpty();
    }

    /**
     * <strong>未解決の例外があるときは起票しない</strong>（[ADR-024] 決定 2）。
     *
     * <p>2 件目を許すと、発生前の状態が上書きされて解決しても戻れない。
     */
    @Test
    @DisplayName("すでに例外があるときは、起票しない")
    void doesNotStackOnAnUnresolvedException() {
        stored = stored.raiseException(ExceptionType.DELAY, "遅延しています", AT);

        useCase.onCustomsStatusChanged(NUMBER, "HELD", "書類不備", AT);

        assertThat(stored.activeException().orElseThrow().exceptionType())
                .as("2 件目を起票している。解決したときに戻る先が失われる")
                .isEqualTo(ExceptionType.DELAY);
        assertThat(savedExceptions).isEmpty();
    }

    /** 知らない追跡番号では止まらない。例外にすると後続のイベントも処理されなくなる。 */
    @Test
    @DisplayName("知らない追跡番号では止まらない")
    void doesNotFailForAnUnknownTrackingNumber() {
        assertThatCode(() -> useCase.onCustomsStatusChanged("TRK-20260823-9999", "HELD", "理由",
                AT)).doesNotThrowAnyException();
        assertThat(savedExceptions).isEmpty();
    }

    /**
     * <strong>手で起票できる種別は増えていない</strong>（[ADR-024] 決定 11）。
     *
     * <p>自動で起票できるようになったからといって、追跡管理者の画面の選択肢に
     * 税関保留が現れてはいけない。<strong>入口が違う</strong>——仕組みが検知する種別は
     * {@code detectException} から入る（IT9 返済枠 0.4）。
     */
    @Test
    @DisplayName("税関保留は、手では起票できないまま")
    void keepsCustomsHoldOutOfTheOperatorEntry() {
        assertThatThrownBy(() -> ExceptionType.parseRaisable("CUSTOMS_HOLD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("手では起票できません");
        assertThat(ExceptionType.raisableTypes()).doesNotContain(ExceptionType.CUSTOMS_HOLD);
    }

    /** <strong>税関保留は緊急ではない</strong>（[ADR-025] 決定 2）。荷主が直ちに動く事象ではない。 */
    @Test
    @DisplayName("税関保留は緊急にしない")
    void isNotUrgent() {
        useCase.onCustomsStatusChanged(NUMBER, "HELD", "書類不備", AT);

        assertThat(stored.hasUrgentException())
                .as("留置で荷主に緊急が届いている。督促の相手は税関と社内である")
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
