package com.example.trackingms.interfaces.rest;

import com.example.trackingms.domain.model.aggregates.TrackingActivity;
import com.example.trackingms.domain.model.valueobjects.TrackingEvent;
import com.example.trackingms.domain.model.entities.TrackingExceptionEvent;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 追跡管理者が見る 1 件（US17-1）。
 *
 * <p>公開照会（{@link PublicTrackingResponse}）より<strong>多くを返す</strong>——
 * こちらは認証があり、業務のために予約番号と例外の中身が要る。
 *
 * <p>2 つを 1 つの DTO にまとめない。まとめると、公開側に出してはいけない項目が
 * <strong>「たまたま今は null」で守られる</strong>形になる。
 */
public record ManagedTrackingResponse(String trackingNumber, String bookingId, String status,
        String statusLabel, String locationName, LocalDate estimatedArrival,
        ManagedExceptionEvent activeException, List<ManagedEvent> events,
        List<ResolvedExceptionEvent> exceptionHistory) {

    /** 起票された例外。<strong>中身まで返す</strong>——対応するのは業務の担当者である。 */
    public record ManagedExceptionEvent(Long id, String exceptionType, String label, String description,
            String occurredAt, boolean urgent) {

        static ManagedExceptionEvent from(TrackingExceptionEvent exception, ZoneId zone) {
            return new ManagedExceptionEvent(exception.id(), exception.exceptionType().name(),
                    exception.exceptionType().label(), exception.description(),
                    PublicTrackingResponse.display(exception.occurredAt(), zone),
                    exception.urgent());
        }
    }

    /** 経過の 1 件。公開側と同じ形にする——荷主と担当者が同じ経過を読む。 */
    public record ManagedEvent(String occurredAt, String status, String statusLabel,
            String locationName, String source) {

        static ManagedEvent from(TrackingEvent event, ZoneId zone) {
            return new ManagedEvent(PublicTrackingResponse.display(event.occurredAt(), zone),
                    event.trackingStatus().name(), event.trackingStatus().label(),
                    event.location().name(), event.source().name());
        }
    }

    /**
     * 起きた例外の記録（US19-5）。<strong>解決したものも含む</strong>。
     *
     * <p>「先週の遅れはどうなったのか」と荷主から問い合わせが来たとき、担当者はこれを
     * 読む。解決したら見えなくなる、では業務が回らない。
     */
    public record ResolvedExceptionEvent(String exceptionType, String label, String description,
            String occurredAt, String resolvedAt, String resolutionNotes, boolean urgent) {

        static ResolvedExceptionEvent from(TrackingExceptionEvent exception, ZoneId zone) {
            return new ResolvedExceptionEvent(exception.exceptionType().name(),
                    exception.exceptionType().label(), exception.description(),
                    PublicTrackingResponse.display(exception.occurredAt(), zone),
                    exception.resolvedAt() == null ? null
                            : PublicTrackingResponse.display(exception.resolvedAt(), zone),
                    exception.resolutionNotes(), exception.urgent());
        }
    }

    static ManagedTrackingResponse from(TrackingActivity activity, List<TrackingEvent> events,
            List<TrackingExceptionEvent> exceptionHistory, ZoneId zone) {
        return new ManagedTrackingResponse(
                activity.trackingNumber().value(),
                activity.bookingId().value(),
                activity.trackingStatus().name(),
                activity.trackingStatus().label(),
                activity.currentLocation().name(),
                activity.estimatedArrival().orElse(null),
                activity.activeException().map(exception -> ManagedExceptionEvent.from(exception, zone))
                        .orElse(null),
                events.stream().map(event -> ManagedEvent.from(event, zone)).toList(),
                exceptionHistory.stream().map(e -> ResolvedExceptionEvent.from(e, zone)).toList());
    }
}
