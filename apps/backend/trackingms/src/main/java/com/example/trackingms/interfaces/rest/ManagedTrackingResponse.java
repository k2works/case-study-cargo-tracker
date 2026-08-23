package com.example.trackingms.interfaces.rest;

import com.example.trackingms.domain.model.TrackingActivity;
import com.example.trackingms.domain.model.TrackingEvent;
import com.example.trackingms.domain.model.TrackingException;
import java.time.LocalDate;
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
        ManagedException activeException, List<ManagedEvent> events) {

    /** 起票された例外。<strong>中身まで返す</strong>——対応するのは業務の担当者である。 */
    public record ManagedException(Long id, String exceptionType, String label, String description,
            String occurredAt, boolean urgent) {

        static ManagedException from(TrackingException exception) {
            return new ManagedException(exception.id(), exception.exceptionType().name(),
                    exception.exceptionType().label(), exception.description(),
                    exception.occurredAt().toString(), exception.urgent());
        }
    }

    /** 経過の 1 件。公開側と同じ形にする——荷主と担当者が同じ経過を読む。 */
    public record ManagedEvent(String occurredAt, String status, String statusLabel,
            String locationName, String source) {

        static ManagedEvent from(TrackingEvent event) {
            return new ManagedEvent(event.occurredAt().toString(), event.trackingStatus().name(),
                    event.trackingStatus().label(), event.location().name(),
                    event.source().name());
        }
    }

    static ManagedTrackingResponse from(TrackingActivity activity, List<TrackingEvent> events) {
        return new ManagedTrackingResponse(
                activity.trackingNumber().value(),
                activity.bookingId().value(),
                activity.trackingStatus().name(),
                activity.trackingStatus().label(),
                activity.currentLocation().name(),
                activity.estimatedArrival().orElse(null),
                activity.activeException().map(ManagedException::from).orElse(null),
                events.stream().map(ManagedEvent::from).toList());
    }
}
