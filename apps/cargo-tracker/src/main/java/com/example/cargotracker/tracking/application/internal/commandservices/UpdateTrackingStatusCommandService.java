package com.example.cargotracker.tracking.application.internal.commandservices;

import com.example.cargotracker.shared.application.logging.AuditValue;
import com.example.cargotracker.shared.domain.event.CargoStatusUpdatedEvent;
import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import com.example.cargotracker.tracking.application.internal.outboundservices.acl.PortNames;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingActivity;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingActivityEvent;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingEventType;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingNumber;
import com.example.cargotracker.tracking.domain.repository.TrackingActivityRepository;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 貨物状態の手動更新（US17）。
 *
 * <p><strong>出港・入港は荷役作業員が登録しない。</strong> 船が出入りしたことは荷役の
 * 記録に現れず、手で入れる以外に追跡へ反映する手段が無い。
 */
@Service
public class UpdateTrackingStatusCommandService {

    /** 業務操作ログ（{@code non_functional.md} §4.4）。 */
    private static final Logger AUDIT = LoggerFactory.getLogger("audit.tracking");

    /** 結果。 */
    public enum Outcome {
        /** 更新した。 */
        UPDATED,
        /** 追跡番号が見つからない。 */
        NOT_FOUND,
        /** 業務のルールで受け付けられない（逆行・未知の港など）。 */
        REJECTED,
        /** 楽観的ロックの競合。 */
        CONFLICTED
    }

    /**
     * @param outcome 結果
     * @param reason  受け付けられなかった理由。**そのまま画面に出す**
     */
    public record Result(Outcome outcome, String reason) {
    }

    private final TrackingActivityRepository trackingRepository;
    private final PortNames portNames;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    /** **業務のタイムゾーンで「今」を決める。** UTC で判断すると時差の分だけずれる。 */
    private final java.time.Clock clock;

    public UpdateTrackingStatusCommandService(
            TrackingActivityRepository trackingRepository,
            PortNames portNames,
            org.springframework.context.ApplicationEventPublisher eventPublisher,
            java.time.Clock clock) {
        this.trackingRepository = trackingRepository;
        this.portNames = portNames;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * 状態を手で進める。
     *
     * <p><strong>拒否は例外ではなく結果で返す。</strong> 逆行も未知の港も業務のエラーであり、
     * 500 にすると利用者には障害として見える。
     */
    @Transactional
    public Result update(
            String trackingNumber, TrackingEventType type, String locationUnlocode,
            Instant occurredAt, String actor) {

        TrackingNumber number;
        Location location;
        try {
            number = new TrackingNumber(trackingNumber);
            location = Location.of(locationUnlocode);
        } catch (IllegalArgumentException e) {
            return new Result(Outcome.REJECTED, e.getMessage());
        }

        // **マスタに無い港は受け付けない。** 存在しない場所の記録は追跡の役に立たない
        if (portNames.findNames(java.util.List.of(location.unlocode())).isEmpty()) {
            return new Result(Outcome.REJECTED,
                    "登録されていない港です: " + location.unlocode());
        }

        Optional<TrackingActivity> found = trackingRepository.findByTrackingNumber(number);
        if (found.isEmpty()) {
            return new Result(Outcome.NOT_FOUND, null);
        }
        TrackingActivity tracking = found.get();

        try {
            tracking.updateManually(
                    TrackingActivityEvent.manual(type, occurredAt, location, null, actor),
                    clock.instant());
        } catch (IllegalStateException | IllegalArgumentException e) {
            return new Result(Outcome.REJECTED, e.getMessage());
        }

        if (!trackingRepository.update(tracking)) {
            return new Result(Outcome.CONFLICTED,
                    "別の担当者が先に更新しました。最新の内容を確認してください");
        }

        // **状態が動いたときだけ知らせる**（US17 の受入基準）。入港のように動かない更新で
        // 通知を作ると、荷主に知らせる中身が無い記録が積み上がる。
        // **Booking を呼ばない。** 呼ぶと ADR-012 で消した循環が戻る（ADR-009）
        type.resultingStatus().ifPresent(status ->
                eventPublisher.publishEvent(new CargoStatusUpdatedEvent(
                        tracking.bookingId().value(),
                        number.value(),
                        status.displayName(),
                        occurredAt,
                        location.unlocode(),
                        actor)));

        if (AUDIT.isInfoEnabled()) {
            AUDIT.info("貨物状態の手動更新 追跡番号={} 種別={} 場所={} 状態={} actor={}",
                    AuditValue.sanitize(number.value()), type.name(),
                    location.unlocode(), tracking.transportStatus().name(),
                    AuditValue.sanitize(actor));
        }
        return new Result(Outcome.UPDATED, null);
    }
}
