package com.example.trackingms.application.internal.queryservices;

import com.example.trackingms.domain.exceptions.TrackingActivityNotFoundException;
import com.example.trackingms.domain.model.aggregates.TrackingActivity;
import com.example.trackingms.domain.model.valueobjects.TrackingNumber;
import com.example.trackingms.domain.ports.TrackingActivityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 追跡情報照会サービス（CQRS クエリ側）
 */
@Service
public class TrackingQueryService {

    private final TrackingActivityRepository trackingActivityRepository;

    public TrackingQueryService(TrackingActivityRepository trackingActivityRepository) {
        this.trackingActivityRepository = trackingActivityRepository;
    }

    /**
     * 追跡番号で追跡情報を照会する
     *
     * @param trackingNumber 追跡番号（TRK-XXXXXX 形式）
     * @return 追跡アクティビティ
     * @throws TrackingActivityNotFoundException 追跡番号が存在しない場合
     * @throws IllegalArgumentException 追跡番号フォーマットが不正な場合
     */
    @Transactional(readOnly = true)
    public TrackingActivity findByTrackingNumber(String trackingNumber) {
        TrackingNumber number = new TrackingNumber(trackingNumber);
        return trackingActivityRepository
                .findByTrackingNumber(number)
                .orElseThrow(() -> new TrackingActivityNotFoundException(trackingNumber));
    }
}
