package com.example.trackingms.infrastructure.persistence;

import com.example.shared.domain.model.Location;
import com.example.trackingms.application.port.TrackingActivityRepository;
import com.example.trackingms.domain.model.ExceptionType;
import com.example.trackingms.domain.model.TrackingActivity;
import com.example.trackingms.domain.model.TrackingBookingId;
import com.example.trackingms.domain.model.TrackingEvent;
import com.example.trackingms.domain.model.TrackingException;
import com.example.trackingms.domain.model.TrackingNumber;
import com.example.trackingms.domain.model.TrackingStatus;
import java.util.List;
import java.util.Optional;

/** 追跡の保存先（MyBatis）。 */
public class MyBatisTrackingActivityRepository implements TrackingActivityRepository {

    private final TrackingActivityMapper mapper;
    private final TrackingEventMapper events;
    private final TrackingExceptionMapper exceptions;

    public MyBatisTrackingActivityRepository(TrackingActivityMapper mapper,
            TrackingEventMapper events, TrackingExceptionMapper exceptions) {
        this.mapper = mapper;
        this.events = events;
        this.exceptions = exceptions;
    }

    /**
     * まだ無ければ保存し、すでにあればそれを返す。
     *
     * <p><strong>重複かどうかは一意制約が決める。</strong>事前に読んでから書く形にすると、
     * 同じイベントが同時に 2 通届いたときに双方が「無い」と読んでから書き、後の 1 通が
     * 落ちてデッドレターへ回る。単一の購読者では表面化しないが、並行化した瞬間に壊れる。
     */
    @Override
    public TrackingActivity saveIfAbsent(TrackingActivity activity) {
        mapper.insertIfAbsent(toRecord(activity));
        return findByTrackingNumber(activity.trackingNumber()).orElseThrow();
    }

    /**
     * 状態を更新する。
     *
     * <p><strong>追跡番号で更新する。</strong>id で更新すると、復元していない集約
     * （まだ id を持たないもの）で黙って 0 件更新になる。
     *
     * <p><strong>発生前の状態・現在地・推定到着日も同じ書き込みで動かす</strong>
     * （[ADR-024] 決定 2）。別々にすると、片方だけ書けた行が残る。
     */
    @Override
    public void updateStatus(TrackingActivity activity) {
        mapper.updateStatus(toRecord(activity));
    }

    @Override
    public Optional<TrackingActivity> findByTrackingNumber(TrackingNumber trackingNumber) {
        return Optional.ofNullable(mapper.findByTrackingNumber(trackingNumber.value()))
                .map(row -> toDomain(row, exceptions.findOpen(row.getTrackingNumber())));
    }

    @Override
    public void appendEvent(TrackingNumber trackingNumber, TrackingEvent event) {
        TrackingEventRecord row = new TrackingEventRecord();
        row.setTrackingNumber(trackingNumber.value());
        row.setTrackingStatus(event.trackingStatus().name());
        row.setLocationUnlocode(event.location().unLocode());
        row.setOccurredAt(event.occurredAt());
        row.setSource(event.source().name());
        events.insert(row);
    }

    @Override
    public List<TrackingEvent> findEvents(TrackingNumber trackingNumber, int limit) {
        return events.findByTrackingNumber(trackingNumber.value(), limit).stream()
                .map(MyBatisTrackingActivityRepository::toEvent)
                .toList();
    }

    /**
     * 例外を保存する。
     *
     * <p>未解決の例外があれば起票、無ければ<strong>直前まで未解決だったものを解決する</strong>。
     * 起票と解決を別のメソッドに分けないのは、呼び出し側（ユースケース）が集約の状態から
     * どちらかを選び直すことになり、判定が 2 か所に分かれるためである。
     */
    @Override
    public void saveException(TrackingNumber trackingNumber, TrackingActivity activity) {
        Optional<TrackingException> active = activity.activeException();
        if (active.isPresent()) {
            TrackingException raised = active.orElseThrow();
            if (raised.id() == null) {
                TrackingExceptionRecord row = new TrackingExceptionRecord();
                row.setTrackingNumber(trackingNumber.value());
                row.setExceptionType(raised.exceptionType().name());
                row.setDescription(raised.description());
                row.setOccurredAt(raised.occurredAt());
                exceptions.insert(row);
            }
            return;
        }
        // 解決した。行はまだ DB 側で未解決のままなので、そこへ解決を足す
        Optional<TrackingException> resolved = activity.lastException();
        TrackingExceptionRecord open = exceptions.findOpen(trackingNumber.value());
        if (resolved.isEmpty() || open == null) {
            return;
        }
        open.setResolvedAt(resolved.orElseThrow().resolvedAt());
        open.setResolutionNotes(resolved.orElseThrow().resolutionNotes());
        exceptions.resolve(open);
    }

    @Override
    public List<TrackingException> findExceptions(TrackingNumber trackingNumber, int limit) {
        return exceptions.findByTrackingNumber(trackingNumber.value(), limit).stream()
                .map(MyBatisTrackingActivityRepository::toException)
                .toList();
    }

    @Override
    public List<TrackingActivity> findWithOpenExceptions(int limit) {
        return exceptions.findOpenTrackingNumbers(limit).stream()
                .map(TrackingNumber::restore)
                .map(this::findByTrackingNumber)
                .flatMap(Optional::stream)
                .toList();
    }

    private static TrackingActivityRecord toRecord(TrackingActivity activity) {
        TrackingActivityRecord row = new TrackingActivityRecord();
        row.setTrackingNumber(activity.trackingNumber().value());
        row.setBookingId(activity.bookingId().value());
        row.setTrackingStatus(activity.trackingStatus().name());
        row.setStatusBefore(activity.statusBefore().map(TrackingStatus::name).orElse(null));
        row.setOriginUnlocode(activity.origin().unLocode());
        row.setDestinationUnlocode(activity.destination().unLocode());
        row.setCurrentLocationUnlocode(activity.currentLocation().unLocode());
        row.setArrivalDeadline(activity.arrivalDeadline());
        row.setEstimatedArrival(activity.estimatedArrival().orElse(null));
        return row;
    }

    /** 復元では検査しない。列が無かったころの行が読めなくなる。 */
    private static TrackingActivity toDomain(TrackingActivityRecord row,
            TrackingExceptionRecord open) {
        return TrackingActivity.restore(row.getId(),
                TrackingNumber.restore(row.getTrackingNumber()),
                TrackingBookingId.restore(row.getBookingId()),
                TrackingStatus.valueOf(row.getTrackingStatus()),
                row.getStatusBefore() == null ? null : TrackingStatus.valueOf(row.getStatusBefore()),
                Location.of(row.getOriginUnlocode(), row.getOriginName()),
                Location.of(row.getDestinationUnlocode(), row.getDestinationName()),
                row.getCurrentLocationUnlocode() == null ? null
                        : Location.of(row.getCurrentLocationUnlocode(),
                                row.getCurrentLocationName()),
                row.getArrivalDeadline(),
                row.getEstimatedArrival(),
                open == null ? null : toException(open));
    }

    private static TrackingException toException(TrackingExceptionRecord row) {
        return TrackingException.restore(row.getId(),
                ExceptionType.restore(row.getExceptionType()), row.getDescription(),
                row.getOccurredAt(), row.getResolvedAt(), row.getResolutionNotes());
    }

    private static TrackingEvent toEvent(TrackingEventRecord row) {
        return new TrackingEvent(TrackingStatus.valueOf(row.getTrackingStatus()),
                Location.of(row.getLocationUnlocode(), row.getLocationName()),
                row.getOccurredAt(), TrackingEvent.EventSource.valueOf(row.getSource()));
    }
}
