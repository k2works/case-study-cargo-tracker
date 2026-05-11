package com.example.trackingms.application.internal.commandservices;

import com.example.trackingms.domain.model.aggregates.TrackingActivity;
import com.example.trackingms.domain.model.aggregates.TrackingExceptionEvent;
import com.example.trackingms.domain.model.valueobjects.ExceptionType;
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
     */
    @Transactional
    public TrackingActivity recordException(RecordTrackingExceptionCommand command) {
        TrackingNumber trackingNumber = new TrackingNumber(command.trackingNumber());
        TrackingActivity activity = trackingActivityRepository
                .findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tracking activity not found: " + command.trackingNumber()));

        TrackingExceptionEvent exception = new TrackingExceptionEvent(
                ExceptionType.valueOf(command.exceptionType()),
                command.occurredAt(),
                command.locationUnlocode(),
                command.reason(),
                command.escalationFlag()
        );

        // DAMAGE 固有情報の設定
        if ("DAMAGE".equals(command.exceptionType()) && command.damageDescription() != null) {
            exception.recordDamageDetails(command.damageDescription(), command.photoUrl());
        }
        // LOST 固有情報の設定
        if ("LOST".equals(command.exceptionType()) && command.lastKnownLocation() != null) {
            exception.recordLostDetails(command.lastKnownLocation(), command.lastSeenAt());
        }

        activity.addException(exception);
        trackingActivityRepository.update(activity);

        return activity;
    }

    /**
     * 例外対応内容を更新する
     */
    @Transactional
    public TrackingActivity respondToException(RespondToExceptionCommand command) {
        TrackingNumber trackingNumber = new TrackingNumber(command.trackingNumber());
        TrackingActivity activity = trackingActivityRepository
                .findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tracking activity not found: " + command.trackingNumber()));

        TrackingExceptionEvent exception = activity.getExceptions().stream()
                .filter(e -> command.exceptionId().equals(e.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Exception not found: " + command.exceptionId()));

        exception.respond(command.responseContent(), command.newEstimatedArrival());
        trackingActivityRepository.update(activity);

        return activity;
    }
}
