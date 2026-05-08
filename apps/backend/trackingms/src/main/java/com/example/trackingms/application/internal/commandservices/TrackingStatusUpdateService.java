package com.example.trackingms.application.internal.commandservices;

import com.example.trackingms.domain.model.aggregates.TrackingActivity;
import com.example.trackingms.domain.model.valueobjects.TrackingNumber;
import com.example.trackingms.domain.model.valueobjects.TrackingStatus;
import com.example.trackingms.domain.ports.TrackingActivityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 貨物状態手動更新サービス
 */
@Service
public class TrackingStatusUpdateService {

    private final TrackingActivityRepository trackingActivityRepository;

    public TrackingStatusUpdateService(TrackingActivityRepository trackingActivityRepository) {
        this.trackingActivityRepository = trackingActivityRepository;
    }

    /**
     * 追跡番号を指定して貨物状態を手動更新する
     *
     * @param command 状態更新コマンド
     * @return 更新後の追跡アクティビティ
     * @throws IllegalArgumentException 追跡番号が存在しない場合
     * @throws IllegalArgumentException 無効な状態値の場合
     */
    @Transactional
    public TrackingActivity updateStatus(UpdateTrackingStatusCommand command) {
        TrackingNumber trackingNumber = new TrackingNumber(command.trackingNumber());
        TrackingActivity activity = trackingActivityRepository
                .findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tracking activity not found: " + command.trackingNumber()));

        TrackingStatus newStatus;
        try {
            newStatus = TrackingStatus.valueOf(command.newStatus());
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException("Invalid tracking status: " + command.newStatus());
        }

        activity.updateStatus(newStatus);
        trackingActivityRepository.update(activity);

        return activity;
    }

    /**
     * 追跡番号で追跡情報を取得する（照会用）
     */
    @Transactional(readOnly = true)
    public TrackingActivity findByTrackingNumber(String trackingNumber) {
        return trackingActivityRepository
                .findByTrackingNumber(new TrackingNumber(trackingNumber))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tracking activity not found: " + trackingNumber));
    }
}
