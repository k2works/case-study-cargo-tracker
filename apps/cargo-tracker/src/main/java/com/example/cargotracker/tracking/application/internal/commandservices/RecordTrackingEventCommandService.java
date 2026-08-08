package com.example.cargotracker.tracking.application.internal.commandservices;

import com.example.cargotracker.shared.domain.model.Location;
import com.example.cargotracker.tracking.domain.model.TrackingActivity;
import com.example.cargotracker.tracking.domain.model.TrackingActivityEvent;
import com.example.cargotracker.tracking.domain.model.TrackingEventType;
import com.example.cargotracker.tracking.domain.model.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.TrackingVoyageNumber;
import com.example.cargotracker.tracking.domain.repository.TrackingActivityRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 追跡イベントを記録して輸送状態を進める（US15）。
 *
 * <p>他 BC のイベントを購読したハンドラから呼ばれる。<strong>新しい
 * トランザクションで書く</strong>（{@code REQUIRES_NEW}）。{@code AFTER_COMMIT} の
 * 時点で発行側のトランザクションは終わっており、そのままでは書き込みがコミットされない
 * （ADR-009）。
 */
@Service
public class RecordTrackingEventCommandService {

    private final TrackingActivityRepository trackingRepository;

    public RecordTrackingEventCommandService(TrackingActivityRepository trackingRepository) {
        this.trackingRepository = trackingRepository;
    }

    /**
     * 追跡イベントを記録する。
     *
     * @return 記録の結果
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result recordEvent(
            String trackingNumber,
            TrackingEventType type,
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
                type,
                occurredAt,
                Location.of(locationUnlocode),
                voyageNumber == null ? null : new TrackingVoyageNumber(voyageNumber)));

        return trackingRepository.update(tracking) ? Result.RECORDED : Result.CONFLICTED;
    }

    /** 記録の結果。 */
    public enum Result {
        /** 記録した。 */
        RECORDED,
        /** 追跡レコードが無い。 */
        NOT_FOUND,
        /** 他の更新が先行していた（楽観的ロック）。 */
        CONFLICTED
    }
}
