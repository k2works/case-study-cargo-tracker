package com.example.trackingms.application.internal.commandservices;

import com.example.trackingms.domain.model.aggregates.TrackingActivity;
import com.example.trackingms.domain.model.aggregates.TrackingActivityEvent;
import com.example.trackingms.domain.model.valueobjects.TrackingEventType;
import com.example.trackingms.domain.model.valueobjects.TrackingNumber;
import com.example.trackingms.domain.ports.TrackingActivityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 荷役作業記録サービス
 */
@Service
public class TrackingActivityEventService {

    private final TrackingActivityRepository trackingActivityRepository;

    public TrackingActivityEventService(TrackingActivityRepository trackingActivityRepository) {
        this.trackingActivityRepository = trackingActivityRepository;
    }

    /**
     * 荷役作業を記録し、貨物状態を更新する
     *
     * @param command 荷役記録コマンド
     * @return 更新後の追跡アクティビティ
     * @throws IllegalArgumentException 追跡番号が存在しない場合
     */
    @Transactional
    public TrackingActivity recordHandlingActivity(RecordHandlingActivityCommand command) {
        TrackingNumber trackingNumber = new TrackingNumber(command.trackingNumber());
        TrackingActivity activity = trackingActivityRepository
                .findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tracking activity not found: " + command.trackingNumber()));

        TrackingEventType eventType = TrackingEventType.valueOf(command.eventType());
        TrackingActivityEvent event = new TrackingActivityEvent(
                eventType,
                command.locationUnlocode(),
                command.eventTime(),
                command.voyageNumber()
        );

        activity.addEvent(event);
        trackingActivityRepository.update(activity);

        return activity;
    }
}
