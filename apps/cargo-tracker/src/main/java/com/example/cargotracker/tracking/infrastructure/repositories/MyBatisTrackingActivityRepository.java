package com.example.cargotracker.tracking.infrastructure.repositories;

import com.example.cargotracker.tracking.domain.model.TrackingDestination;
import com.example.cargotracker.tracking.domain.model.ExceptionOccurrence;
import com.example.cargotracker.shared.domain.model.Location;
import com.example.cargotracker.tracking.domain.model.ExceptionType;
import com.example.cargotracker.tracking.domain.model.TrackingActivity;
import com.example.cargotracker.tracking.domain.model.TrackingActivityEvent;
import com.example.cargotracker.tracking.domain.model.TrackingBookingId;
import com.example.cargotracker.tracking.domain.model.TrackingEventSource;
import com.example.cargotracker.tracking.domain.model.TrackingExceptionEvent;
import com.example.cargotracker.tracking.domain.model.TrackingEventType;
import com.example.cargotracker.tracking.domain.model.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.TrackingVoyageNumber;
import com.example.cargotracker.tracking.domain.model.TransportStatus;
import com.example.cargotracker.tracking.domain.repository.TrackingActivityRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** {@link TrackingActivityRepository} の MyBatis 実装（出力アダプタ）。 */
@Repository
public class MyBatisTrackingActivityRepository implements TrackingActivityRepository {

    private final TrackingMapper mapper;

    public MyBatisTrackingActivityRepository(TrackingMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(TrackingActivity activity) {
        mapper.insert(toRecord(activity));
    }

    /**
     * 輸送状態とイベントを保存する。
     *
     * <p><strong>イベントは丸ごと入れ替える。</strong> 差分で足すと、集約が持つ
     * イベントと保存されたイベントがずれたときに、どちらが正しいか分からなくなる。
     * 件数は 1 つの貨物の荷役回数に限られるため、入れ替えの費用は問題にならない。
     */
    @Override
    @Transactional
    public boolean update(TrackingActivity activity) {
        if (mapper.updateStatus(toRecord(activity)) != 1) {
            return false;
        }
        TrackingActivityRecord stored =
                mapper.findByTrackingNumber(activity.trackingNumber().value());
        long trackingId = stored.getId();
        mapper.deleteEvents(trackingId);

        List<TrackingActivityEvent> events = activity.events();
        if (!events.isEmpty()) {
            List<TrackingEventRecord> rows = new ArrayList<>(events.size());
            for (TrackingActivityEvent event : events) {
                rows.add(toEventRecord(trackingId, event));
            }
            mapper.insertEvents(rows);
        }
        return saveExceptions(trackingId, activity);
    }

    /**
     * 例外を保存する。
     *
     * <p><strong>イベントのように丸ごと入れ替えない。</strong> 例外は ID で
     * 参照して解決するため、入れ替えると解決のたびに ID が変わってしまう。
     * 新しく起票されたもの（ID 未採番）だけを足し、解決の記録は更新で書く。
     */
    private boolean saveExceptions(long trackingId, TrackingActivity activity) {
        for (TrackingExceptionEvent exception : activity.exceptions()) {
            if (exception.isNew()) {
                mapper.insertException(toExceptionRecord(trackingId, exception));
            } else if (exception.isResolved()) {
                // **解決済みの行への再更新は SQL 側が弾く**（resolved_at IS NULL）。
                // 同時に 2 つのリクエストが来ると、どちらも「未解決」を読んでいる
                TrackingExceptionRecord row = new TrackingExceptionRecord();
                row.setId(exception.id());
                row.setResolvedAt(exception.resolvedAt());
                row.setResolutionNotes(exception.resolutionNotes());
                if (mapper.resolveException(row) != 1) {
                    // **砦を置いたなら警報も要る。** 0 行のまま成功を返すと、
                    // DB に書けていないのに荷主へ「対応しました」と通知が飛ぶ
                    return false;
                }
            }
        }
        return true;
    }

    private static TrackingExceptionRecord toExceptionRecord(
            long trackingId, TrackingExceptionEvent exception) {
        TrackingExceptionRecord row = new TrackingExceptionRecord();
        row.setTrackingId(trackingId);
        row.setExceptionType(exception.exceptionType().name());
        row.setLocationUnlocode(
                exception.location() == null ? null : exception.location().unlocode());
        row.setOccurredAt(exception.occurredAt());
        row.setDescription(exception.description());
        row.setEscalationFlag(exception.escalationFlag());
        row.setStatusBefore(exception.statusBefore().name());
        return row;
    }

    private static TrackingExceptionEvent toException(TrackingExceptionRecord row) {
        return TrackingExceptionEvent.reconstruct(
                row.getId(),
                new ExceptionOccurrence(
                        ExceptionType.valueOf(row.getExceptionType()),
                        row.getLocationUnlocode() == null
                                ? null : Location.of(row.getLocationUnlocode()),
                        row.getOccurredAt(),
                        row.getDescription()),
                row.isEscalationFlag(),
                TransportStatus.valueOf(row.getStatusBefore()),
                row.getResolvedAt(),
                row.getResolutionNotes());
    }

    @Override
    public Optional<TrackingActivity> findByTrackingNumber(TrackingNumber trackingNumber) {
        return toDomain(mapper.findByTrackingNumber(trackingNumber.value()));
    }

    @Override
    public Optional<TrackingActivity> findByBookingId(TrackingBookingId bookingId) {
        return toDomain(mapper.findByBookingId(bookingId.value()));
    }

    private Optional<TrackingActivity> toDomain(TrackingActivityRecord row) {
        if (row == null) {
            return Optional.empty();
        }
        // **イベントも一緒に読む。** 落とすと、状態はあるのに履歴が空の追跡になる
        List<TrackingActivityEvent> events = mapper.findEvents(row.getId()).stream()
                .map(MyBatisTrackingActivityRepository::toEvent)
                .toList();
        // **例外も一緒に読む。** 落とすと、解決しようとした例外が「無い」ことになり、
        // 例外発生のまま戻せない貨物ができる
        List<TrackingExceptionEvent> exceptions = mapper.findExceptions(row.getId()).stream()
                .map(MyBatisTrackingActivityRepository::toException)
                .toList();
        return Optional.of(TrackingActivity.reconstruct(
                new TrackingNumber(row.getTrackingNumber()),
                new TrackingBookingId(row.getBookingId()),
                TransportStatus.valueOf(row.getTransportStatus()),
                events,
                exceptions,
                row.getVersion(),
                new TrackingDestination(
                        row.getDestinationUnlocode() == null
                                ? null : Location.of(row.getDestinationUnlocode()),
                        row.getEstimatedArrivalDate())));
    }

    private static TrackingActivityEvent toEvent(TrackingEventRecord row) {
        return new TrackingActivityEvent(
                TrackingEventType.valueOf(row.getEventType()),
                row.getEventTime(),
                Location.of(row.getLocationUnlocode()),
                row.getVoyageNumber() == null
                        ? null : new TrackingVoyageNumber(row.getVoyageNumber()),
                TrackingEventSource.valueOf(row.getSource()),
                row.getRecordedBy());
    }

    private static TrackingActivityRecord toRecord(TrackingActivity activity) {
        TrackingActivityRecord row = new TrackingActivityRecord();
        row.setTrackingNumber(activity.trackingNumber().value());
        row.setBookingId(activity.bookingId().value());
        row.setTransportStatus(activity.transportStatus().name());
        row.setVersion(activity.version());
        row.setDestinationUnlocode(activity.destination().location() == null
                ? null : activity.destination().location().unlocode());
        row.setEstimatedArrivalDate(activity.destination().estimatedArrival());
        return row;
    }

    private static TrackingEventRecord toEventRecord(long trackingId, TrackingActivityEvent e) {
        TrackingEventRecord row = new TrackingEventRecord();
        row.setTrackingId(trackingId);
        row.setEventType(e.type().name());
        row.setEventTime(e.occurredAt());
        row.setLocationUnlocode(e.location().unlocode());
        row.setVoyageNumber(e.voyageNumber() == null ? null : e.voyageNumber().value());
        row.setSource(e.source().name());
        row.setRecordedBy(e.recordedBy());
        return row;
    }
}
