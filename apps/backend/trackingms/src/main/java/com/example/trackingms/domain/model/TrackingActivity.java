package com.example.trackingms.domain.model;

import com.example.shared.domain.model.Location;
import java.time.LocalDate;

/**
 * 貨物の追跡（集約ルート）。
 *
 * <p>IT6 で作れるのは<strong>追跡の開始まで</strong>である。荷役イベントと例外の起票は
 * US15 以降で足す。<strong>縮小実装であることを明記する</strong>——書かないと実装漏れと
 * 読まれる。
 *
 * <p>追跡番号は<strong>受け取る</strong>（[ADR-022] 決定 7）。ここでは採番しない。
 */
public final class TrackingActivity {

    private final Long id;
    private final TrackingNumber trackingNumber;
    private final TrackingBookingId bookingId;
    private final TransportStatus transportStatus;
    private final Location origin;
    private final Location destination;
    private final LocalDate arrivalDeadline;

    private TrackingActivity(Long id, TrackingNumber trackingNumber, TrackingBookingId bookingId,
            TransportStatus transportStatus, Location origin, Location destination,
            LocalDate arrivalDeadline) {
        this.id = id;
        this.trackingNumber = trackingNumber;
        this.bookingId = bookingId;
        this.transportStatus = transportStatus;
        this.origin = origin;
        this.destination = destination;
        this.arrivalDeadline = arrivalDeadline;
    }

    /**
     * 追跡を始める。ここでだけ入力を検査する。
     *
     * <p>状態は空欄にせず、意味のある初期値を置く（[ADR-009]）。貨物はまだ動いていない。
     */
    public static TrackingActivity start(TrackingNumber trackingNumber,
            TrackingBookingId bookingId, Location origin, Location destination,
            LocalDate arrivalDeadline) {
        if (trackingNumber == null) {
            throw new IllegalArgumentException("追跡番号は必須です");
        }
        if (bookingId == null) {
            throw new IllegalArgumentException("予約番号は必須です");
        }
        if (origin == null || destination == null) {
            throw new IllegalArgumentException("出発地と目的地は必須です");
        }
        if (arrivalDeadline == null) {
            throw new IllegalArgumentException("到着期限は必須です");
        }
        return new TrackingActivity(null, trackingNumber, bookingId,
                TransportStatus.NOT_RECEIVED, origin, destination, arrivalDeadline);
    }

    /** 永続化された行から復元する。ここでは検査しない。 */
    public static TrackingActivity restore(Long id, TrackingNumber trackingNumber,
            TrackingBookingId bookingId, TransportStatus transportStatus, Location origin,
            Location destination, LocalDate arrivalDeadline) {
        return new TrackingActivity(id, trackingNumber, bookingId, transportStatus, origin,
                destination, arrivalDeadline);
    }

    public Long id() {
        return id;
    }

    public TrackingNumber trackingNumber() {
        return trackingNumber;
    }

    public TrackingBookingId bookingId() {
        return bookingId;
    }

    public TransportStatus transportStatus() {
        return transportStatus;
    }

    public Location origin() {
        return origin;
    }

    public Location destination() {
        return destination;
    }

    public LocalDate arrivalDeadline() {
        return arrivalDeadline;
    }
}
