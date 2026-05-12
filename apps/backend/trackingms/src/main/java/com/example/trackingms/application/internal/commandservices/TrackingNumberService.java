package com.example.trackingms.application.internal.commandservices;

import com.example.trackingms.domain.events.TrackingNumberIssuedEvent;
import com.example.trackingms.domain.model.aggregates.TrackingActivity;
import com.example.trackingms.domain.model.valueobjects.TrackingBookingId;
import com.example.trackingms.domain.model.valueobjects.TrackingNumber;
import com.example.trackingms.domain.ports.TrackingActivityRepository;
import com.example.trackingms.domain.ports.TrackingEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 追跡番号発行サービス
 */
@Service
public class TrackingNumberService {
    private final TrackingActivityRepository trackingActivityRepository;
    private final TrackingEventPublisher trackingEventPublisher;

    public TrackingNumberService(TrackingActivityRepository trackingActivityRepository,
                                 TrackingEventPublisher trackingEventPublisher) {
        this.trackingActivityRepository = trackingActivityRepository;
        this.trackingEventPublisher = trackingEventPublisher;
    }

    /**
     * 予約 ID に対して追跡番号を発行する
     * 既に発行済みの場合は既存の追跡番号を返す
     *
     * <p>新規発行・既存返却のどちらの場合でも TrackingNumberIssuedEvent を発行する。
     * bookingms 側のリスナーは CONFIRMED 状態のときのみ TRACKING_ISSUED に遷移する
     * 冪等な実装になっているため、再送による副作用はない。
     * これにより bookingms と trackingms の DB が再起動などで非対称にリセットされた場合でも、
     * ユーザーが再度発行ボタンを押すことで bookingms 側のステータスを追いつかせられる。
     *
     * @param bookingId 予約 ID
     * @return 追跡アクティビティ（追跡番号を含む）
     */
    @Transactional
    public TrackingActivity issueTrackingNumber(String bookingId) {
        TrackingBookingId trackingBookingId = new TrackingBookingId(bookingId);

        TrackingActivity activity = trackingActivityRepository.findByBookingId(trackingBookingId)
                .orElseGet(() -> {
                    TrackingNumber trackingNumber = generateTrackingNumber();
                    TrackingActivity newActivity = new TrackingActivity(trackingNumber, trackingBookingId);
                    return trackingActivityRepository.save(newActivity);
                });

        trackingEventPublisher.publishTrackingNumberIssued(
                new TrackingNumberIssuedEvent(bookingId, activity.getTrackingNumber().number()));

        return activity;
    }

    /**
     * 追跡番号を生成する（TRK-XXXXXX 形式、DB シーケンス使用）
     */
    private TrackingNumber generateTrackingNumber() {
        long seq = trackingActivityRepository.nextTrackingNumberSequence();
        return new TrackingNumber(String.format("TRK-%06d", seq));
    }
}
