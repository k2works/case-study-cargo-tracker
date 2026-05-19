package com.example.cargotracker.trackingms.application.query;

import com.example.cargotracker.trackingms.infrastructure.persistence.TrackingEventMapper;
import com.example.cargotracker.trackingms.infrastructure.persistence.TrackingExceptionMapper;
import com.example.cargotracker.trackingms.infrastructure.persistence.TrackingExceptionRecord;
import com.example.cargotracker.trackingms.infrastructure.persistence.TrackingSummaryMapper;
import com.example.cargotracker.trackingms.infrastructure.persistence.TrackingSummaryRecord;
import com.example.cargotracker.trackingms.interfaces.rest.dto.TrackingInfoResponse;
import com.example.cargotracker.trackingms.interfaces.rest.dto.TrackingListItemResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * US18 公開追跡照会の Query Side サービス。
 *
 * <p>{@code tracking_summary} + {@code tracking_event} を集約して
 * {@link TrackingInfoResponse} を組み立てる。Aggregate / Event Store には触れない（CQRS の Q 側）。</p>
 */
@Service
public class TrackingQueryService {

    private final TrackingSummaryMapper summaryMapper;
    private final TrackingEventMapper eventMapper;
    private final TrackingExceptionMapper exceptionMapper;

    public TrackingQueryService(
            TrackingSummaryMapper summaryMapper,
            TrackingEventMapper eventMapper,
            TrackingExceptionMapper exceptionMapper) {
        this.summaryMapper = summaryMapper;
        this.eventMapper = eventMapper;
        this.exceptionMapper = exceptionMapper;
    }

    public Optional<TrackingInfoResponse> findByTrackingNumber(
            String trackingNumber, LocalDateTime validUntil) {
        Optional<TrackingSummaryRecord> summaryOpt = summaryMapper.findByTrackingNumber(trackingNumber);
        if (summaryOpt.isEmpty()) {
            return Optional.empty();
        }
        TrackingSummaryRecord summary = summaryOpt.get();
        List<TrackingInfoResponse.TrackingEvent> events =
                eventMapper.findByTrackingNumberOrderByOccurredAtDesc(trackingNumber)
                        .stream()
                        .map(event -> new TrackingInfoResponse.TrackingEvent(
                                event.occurredAt(),
                                event.eventType(),
                                event.unlocode(),
                                event.voyageNumber(),
                                event.transportStatus(),
                                event.handlingType(),
                                event.source(),
                                event.description()))
                        .toList();

        return Optional.of(new TrackingInfoResponse(
                summary.trackingNumber(),
                summary.currentStatus(),
                new TrackingInfoResponse.Location(summary.currentUnlocode(), null),
                summary.estimatedArrival(),
                summary.deliveredAt(),
                summary.misrouted(),
                validUntil,
                events));
    }

    public Optional<LocalDateTime> findDeliveredAt(String trackingNumber) {
        return summaryMapper.findByTrackingNumber(trackingNumber)
                .map(TrackingSummaryRecord::deliveredAt);
    }

    /**
     * 追跡番号に対応する {@code tracking_summary} が存在するかを返す。
     *
     * <p>{@code PUT /api/v1/tracking/{tn}/status} の事前チェックに使用する。
     * 未初期化の追跡集約への状態更新で発生する FK 制約違反を防ぐ。</p>
     */
    public boolean exists(String trackingNumber) {
        return summaryMapper.findByTrackingNumber(trackingNumber).isPresent();
    }

    /**
     * US20 追跡例外一覧取得。
     */
    public List<TrackingExceptionRecord> findExceptions(String trackingNumber) {
        return exceptionMapper.findByTrackingNumber(trackingNumber);
    }

    /**
     * S16 追跡管理一覧用の全件取得（最終更新日時降順）。
     */
    public List<TrackingListItemResponse> findAll() {
        return summaryMapper.findAllOrderByUpdatedAtDesc()
                .stream()
                .map(summary -> new TrackingListItemResponse(
                        summary.trackingNumber(),
                        summary.bookingId(),
                        summary.currentStatus(),
                        summary.currentUnlocode(),
                        summary.originUnlocode(),
                        summary.destinationUnlocode(),
                        summary.estimatedArrival(),
                        summary.deliveredAt(),
                        summary.misrouted(),
                        summary.lastEventAt(),
                        summary.updatedAt()))
                .toList();
    }
}
