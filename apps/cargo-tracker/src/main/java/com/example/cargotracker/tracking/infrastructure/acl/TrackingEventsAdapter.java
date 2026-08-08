package com.example.cargotracker.tracking.infrastructure.acl;

import com.example.cargotracker.handling.application.internal.outboundservices.acl.TrackingEvents;
import com.example.cargotracker.shared.domain.model.Location;
import com.example.cargotracker.tracking.domain.model.TrackingActivity;
import com.example.cargotracker.tracking.domain.model.TrackingActivityEvent;
import com.example.cargotracker.tracking.domain.model.TrackingEventType;
import com.example.cargotracker.tracking.domain.model.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.TrackingVoyageNumber;
import com.example.cargotracker.tracking.domain.repository.TrackingActivityRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link TrackingEvents} の実装（ACL のアダプタ）。
 *
 * <p><strong>荷役種別を追跡イベント種別へ翻訳するのはここである。</strong> 値が同じでも
 * 「荷役として何をしたか」と「追跡の上で何が起きたか」は別の事実であり、
 * 対応づけは境界の仕事である（ADR-005）。
 */
@Component
public class TrackingEventsAdapter implements TrackingEvents {

    private final TrackingActivityRepository trackingRepository;

    public TrackingEventsAdapter(TrackingActivityRepository trackingRepository) {
        this.trackingRepository = trackingRepository;
    }

    @Override
    @Transactional
    public Result record(
            String trackingNumber,
            String eventType,
            Instant occurredAt,
            String locationUnlocode,
            String voyageNumber) {

        Optional<TrackingActivity> found =
                trackingRepository.findByTrackingNumber(new TrackingNumber(trackingNumber));
        if (found.isEmpty()) {
            return Result.NOT_FOUND;
        }
        TrackingActivity tracking = found.get();
        tracking.recordEvent(new TrackingActivityEvent(
                toEventType(eventType),
                occurredAt,
                Location.of(locationUnlocode),
                voyageNumber == null ? null : new TrackingVoyageNumber(voyageNumber)));

        // **衝突の合図を捨てない。** update が false のときは輸送状態もイベントも
        // 書かれていない。捨てると「登録しました」と出たまま追跡だけが取り残される
        return trackingRepository.update(tracking) ? Result.RECORDED : Result.CONFLICTED;
    }

    /**
     * 荷役種別を追跡イベント種別へ翻訳する。
     *
     * <p><strong>名前が一致していることに依存しない。</strong> 依存すると、荷役側に
     * 種別を 1 つ足した瞬間にコンパイルは通り、実行時に落ちる。
     */
    private static TrackingEventType toEventType(String handlingType) {
        return switch (handlingType) {
            case "RECEIVE" -> TrackingEventType.RECEIVE;
            case "LOAD" -> TrackingEventType.LOAD;
            case "UNLOAD" -> TrackingEventType.UNLOAD;
            case "CUSTOMS" -> TrackingEventType.CUSTOMS;
            case "CLAIM" -> TrackingEventType.CLAIM;
            default -> throw new IllegalArgumentException(
                    "追跡イベントに対応しない荷役種別です: " + handlingType);
        };
    }
}
