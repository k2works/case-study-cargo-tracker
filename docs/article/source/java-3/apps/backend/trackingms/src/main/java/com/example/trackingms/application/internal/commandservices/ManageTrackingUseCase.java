package com.example.trackingms.application.internal.commandservices;

import com.example.shared.domain.model.Location;
import com.example.trackingms.application.internal.queryservices.TrackingLookupUseCase;
import com.example.trackingms.domain.repository.LocationRepository;
import com.example.trackingms.domain.repository.TrackingActivityRepository;
import com.example.trackingms.application.internal.outboundservices.acl.TrackingNotifier;
import com.example.trackingms.domain.model.valueobjects.ExceptionType;
import com.example.trackingms.domain.model.aggregates.TrackingActivity;
import com.example.trackingms.domain.model.valueobjects.TrackingEvent;
import com.example.trackingms.domain.model.entities.TrackingExceptionEvent;
import com.example.trackingms.domain.model.valueobjects.TrackingNumber;
import com.example.trackingms.domain.model.valueobjects.TrackingStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

/**
 * 貨物の状態を手で更新し、例外を起票・解決する（US17・US19・US20）。
 *
 * <p><strong>トランザクションの境目はここに置く。</strong>状態の更新と経過の記録が別々の
 * トランザクションになると、状態は動いたのに経過に出ない行ができ、荷主は「いつ変わったか」を
 * 読めない（IT6・IT7 と同じ形）。
 */
@Service
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
        return activities.findWithOpenExceptions(OPEN_EXCEPTION_LIMIT).stream()
                .sorted(OPEN_EXCEPTION_ORDER)
                .toList();
    }

    /**
     * 一覧の並び順（IT9 返済枠 0.8・IT8 レビュー #17）。
     *
     * <p><strong>緊急を先に、次に古い順。</strong>20 件を超えると、並んでいない一覧は
     * 「上から順に見る」ことができなくなり、担当者は毎朝すべてを読み直すことになる。
     * 古い順にするのは、<strong>最も長く放置されているものが最も危ない</strong>ためである。
     * 新しい順にすると、古い 1 件が下へ沈み続ける。
     *
     * <p><strong>緊急かどうかは集約の述語をそのまま呼ぶ。</strong>SQL に種別名を書くと、
     * 緊急の定義（[ADR-024] 決定 3。紛失だけ）が 2 か所になる。
     */
    private static final Comparator<TrackingActivity> OPEN_EXCEPTION_ORDER =
            Comparator.comparing(TrackingActivity::hasUrgentException).reversed()
                    .thenComparing(activity -> activity.activeException()
                            .map(TrackingExceptionEvent::occurredAt)
                            .orElse(Instant.MAX));

    /** 一覧に出す貨物の上限。**朝の一覧としてこれ以上は読めない**。 */
    public static final int OPEN_EXCEPTION_LIMIT = 100;

    /**
     * 1 つの貨物に起きた例外を、解決済みも含めて返す（US19-5）。
     *
     * <p><strong>解決したら見えなくなる、では業務が回らない。</strong>
     */
    public List<TrackingExceptionEvent> exceptions(
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
                    TrackingStatus.parse(status), location, occurredAt);
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
    public Optional<TrackingActivity> resolveException(String trackingNumber, Long exceptionId,
            String resolutionNotes, LocalDate newEstimatedArrival) {
        return find(trackingNumber).map(activity -> {
            Instant now = clock.instant();
            TrackingActivity resolved =
                    activity.resolveException(exceptionId, resolutionNotes, now, newEstimatedArrival);
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

}
