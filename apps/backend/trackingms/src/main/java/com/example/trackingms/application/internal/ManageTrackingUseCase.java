package com.example.trackingms.application.internal;

import com.example.shared.domain.model.Location;
import com.example.trackingms.application.port.LocationRepository;
import com.example.trackingms.application.port.TrackingActivityRepository;
import com.example.trackingms.application.port.TrackingNotifier;
import com.example.trackingms.domain.model.ExceptionType;
import com.example.trackingms.domain.model.TrackingActivity;
import com.example.trackingms.domain.model.TrackingEvent;
import com.example.trackingms.domain.model.TrackingNumber;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * 貨物の状態を手で更新し、例外を起票・解決する（US17・US19・US20）。
 *
 * <p><strong>トランザクションの境目はここに置く。</strong>状態の更新と経過の記録が別々の
 * トランザクションになると、状態は動いたのに経過に出ない行ができ、荷主は「いつ変わったか」を
 * 読めない（IT6・IT7 と同じ形）。
 */
public class ManageTrackingUseCase {

    private final TrackingActivityRepository activities;
    private final LocationRepository locations;
    private final TrackingNotifier notifier;
    private final Clock clock;

    public ManageTrackingUseCase(TrackingActivityRepository activities,
            LocationRepository locations, TrackingNotifier notifier, Clock clock) {
        this.activities = activities;
        this.locations = locations;
        this.notifier = notifier;
        this.clock = clock;
    }

    /** 1 件を開く（US17-1）。 */
    public Optional<TrackingActivity> find(String trackingNumber) {
        return activities.findByTrackingNumber(TrackingNumber.of(trackingNumber));
    }

    /** 経過（US18-3）。 */
    public List<TrackingEvent> events(TrackingActivity activity) {
        return activities.findEvents(activity.trackingNumber(), TrackingLookupUseCase.HISTORY_LIMIT);
    }

    /**
     * 未解決の例外がある貨物（横断規約）。<strong>件数の遷移先である</strong>。
     *
     * <p>上限は経過のものと分ける。経過の上限（200）の根拠は「1 つの貨物の経過が
     * 200 を超えることは実務では無い」であり、<strong>貨物の件数には成り立たない</strong>。
     */
    public List<TrackingActivity> withOpenExceptions() {
        return activities.findWithOpenExceptions(OPEN_EXCEPTION_LIMIT);
    }

    /** 一覧に出す貨物の上限。**朝の一覧としてこれ以上は読めない**。 */
    public static final int OPEN_EXCEPTION_LIMIT = 100;

    /**
     * 1 つの貨物に起きた例外を、解決済みも含めて返す（US19-5）。
     *
     * <p><strong>解決したら見えなくなる、では業務が回らない。</strong>
     */
    public List<com.example.trackingms.domain.model.TrackingException> exceptions(
            TrackingActivity activity) {
        return activities.findExceptions(activity.trackingNumber(),
                TrackingLookupUseCase.HISTORY_LIMIT);
    }

    /**
     * 状態を手で更新する（US17-2・US17-3）。
     *
     * <p>判定は集約が持つ。ここで「戻る向きか」を見比べると、規則がユースケースと集約の
     * 2 か所に分かれる。
     *
     * @throws IllegalArgumentException 地点がマスタに無いとき、または進む向きでないとき
     */
    @Transactional
    public Optional<TrackingActivity> updateStatus(String trackingNumber, String status,
            String locationUnLocode, Instant occurredAt) {
        return find(trackingNumber).map(activity -> {
            Location location = requireLocation(locationUnLocode);
            TrackingActivity updated = activity.updateManually(
                    parseStatus(status), location, occurredAt);
            activities.updateStatus(updated);
            // **状態が動いたら、経過にも残す。**同じトランザクションで書く
            activities.appendEvent(updated.trackingNumber(), new TrackingEvent(
                    updated.trackingStatus(), location, occurredAt,
                    TrackingEvent.EventSource.MANUAL));
            notifier.statusChanged(updated);
            return updated;
        });
    }

    /** 例外を起票する（US19-1・US19-2・US20-1・US20-2）。 */
    @Transactional
    public Optional<TrackingActivity> raiseException(String trackingNumber, String exceptionType,
            String description) {
        return find(trackingNumber).map(activity -> {
            Instant now = clock.instant();
            TrackingActivity raised = activity.raiseException(
                    ExceptionType.parseRaisable(exceptionType), description, now);
            activities.updateStatus(raised);
            activities.saveException(raised.trackingNumber(), raised);
            activities.appendEvent(raised.trackingNumber(), new TrackingEvent(
                    raised.trackingStatus(), raised.currentLocation(), now,
                    TrackingEvent.EventSource.EXCEPTION));
            notifier.exceptionRaised(raised);
            return raised;
        });
    }

    /**
     * 例外を解決する（US19-4）。
     *
     * <p><strong>開き直してから解決する。</strong>集約を持ち回らず、保存先から復元した
     * ものを使う——発生前の状態が行に残っていないと、ここで戻る先が分からなくなる
     * （[ADR-024] 決定 2）。
     */
    @Transactional
    public Optional<TrackingActivity> resolveException(String trackingNumber,
            String resolutionNotes, LocalDate newEstimatedArrival) {
        return find(trackingNumber).map(activity -> {
            Instant now = clock.instant();
            TrackingActivity resolved =
                    activity.resolveException(resolutionNotes, now, newEstimatedArrival);
            activities.updateStatus(resolved);
            activities.saveException(resolved.trackingNumber(), resolved);
            activities.appendEvent(resolved.trackingNumber(), new TrackingEvent(
                    resolved.trackingStatus(), resolved.currentLocation(), now,
                    TrackingEvent.EventSource.EXCEPTION));
            notifier.exceptionResolved(resolved);
            return resolved;
        });
    }

    private Location requireLocation(String unLocode) {
        if (unLocode == null || unLocode.isBlank()) {
            throw new IllegalArgumentException("現在地を選んでください");
        }
        return locations.findByUnLocode(unLocode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "地点マスタにない場所です: " + unLocode));
    }

    /**
     * 状態の名前を読む。
     *
     * <p><strong>読み方を入口ごとに書かない。</strong>入口が増えた日に、状態の不正が
     * 別の見え方をする（返済枠 0.5 と同じ形）。
     */
    private static com.example.trackingms.domain.model.TrackingStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("新しい状態を選んでください");
        }
        try {
            return com.example.trackingms.domain.model.TrackingStatus.valueOf(status);
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException("状態が不正です: " + status);
        }
    }
}
