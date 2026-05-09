package com.example.trackingms.application.internal.commandservices;

import com.example.trackingms.domain.model.aggregates.TrackingActivity;
import com.example.trackingms.domain.model.aggregates.TrackingExceptionEvent;
import com.example.trackingms.domain.model.valueobjects.TrackingNumber;
import com.example.trackingms.domain.ports.TrackingActivityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 遅延例外処理サービス
 */
@Service
public class TrackingExceptionService {

    private final TrackingActivityRepository trackingActivityRepository;

    public TrackingExceptionService(TrackingActivityRepository trackingActivityRepository) {
        this.trackingActivityRepository = trackingActivityRepository;
    }

    /**
     * 遅延例外を記録し、貨物状態を EXCEPTION に更新する
     *
     * @param command 例外記録コマンド
     * @return 更新後の追跡アクティビティ
     * @throws IllegalArgumentException 追跡番号が存在しない場合
     */
    @Transactional
    public TrackingActivity recordException(RecordTrackingExceptionCommand command) {
        TrackingNumber trackingNumber = new TrackingNumber(command.trackingNumber());
        TrackingActivity activity = trackingActivityRepository
                .findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tracking activity not found: " + command.trackingNumber()));

        TrackingExceptionEvent exception = new TrackingExceptionEvent(
                command.exceptionType(),
                command.occurredAt(),
                command.locationUnlocode(),
                command.reason(),
                command.escalationFlag()
        );

        activity.addException(exception);
        trackingActivityRepository.update(activity);

        return activity;
    }
}
