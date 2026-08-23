package com.example.trackingms.domain.model;

import com.example.shared.domain.model.Location;
import java.time.LocalDate;

/**
 * 貨物の追跡（集約ルート）。
 *
 * <p>IT7 で進むのは<strong>荷役に応じた状態の遷移まで</strong>である。例外の起票（US20）と
 * 出港の反映（US17）は IT8 で足す。<strong>縮小実装であることを明記する</strong>
 * ——書かないと実装漏れと読まれる。
 *
 * <p>追跡番号は<strong>受け取る</strong>（[ADR-022] 決定 7）。ここでは採番しない。
 */
public final class TrackingActivity {

    private final Long id;
    private final TrackingNumber trackingNumber;
    private final TrackingBookingId bookingId;
    private final TrackingStatus trackingStatus;
    private final Location origin;
    private final Location destination;
    private final LocalDate arrivalDeadline;

    private TrackingActivity(Long id, TrackingNumber trackingNumber, TrackingBookingId bookingId,
            TrackingStatus trackingStatus, Location origin, Location destination,
            LocalDate arrivalDeadline) {
        this.id = id;
        this.trackingNumber = trackingNumber;
        this.bookingId = bookingId;
        this.trackingStatus = trackingStatus;
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
                TrackingStatus.NOT_RECEIVED, origin, destination, arrivalDeadline);
    }

    /** 永続化された行から復元する。ここでは検査しない。 */
    public static TrackingActivity restore(Long id, TrackingNumber trackingNumber,
            TrackingBookingId bookingId, TrackingStatus trackingStatus, Location origin,
            Location destination, LocalDate arrivalDeadline) {
        return new TrackingActivity(id, trackingNumber, bookingId, trackingStatus, origin,
                destination, arrivalDeadline);
    }

    /**
     * 荷役の記録に応じて状態を進める（US15-4・[ADR-023] 決定 5）。
     *
     * <p><strong>戻せる遷移は作らない。</strong>荷役は実際に起きた作業であり、記録が届いた
     * 順に進む。届く順が入れ替わることはあるが、そのときに「戻す」と、あとから届いた古い
     * 作業で追跡が巻き戻る。
     *
     * <p>進む先を決めるのは {@link TrackingStatus#afterHandling}。ここで種別を見比べると、
     * 判定が集約と列挙の 2 か所に分かれる。
     *
     * @param handlingType 荷役の種別の名前（相手の型は持ち込まない）
     * @param locationUnLocode 作業場所
     * @return 進めた追跡。進む先が決まらなければ、そのままの自分を返す
     */
    public TrackingActivity afterHandling(String handlingType, String locationUnLocode) {
        boolean atDestination = destination.unLocode().equals(locationUnLocode);
        return TrackingStatus.afterHandling(handlingType, atDestination)
                .map(next -> new TrackingActivity(id, trackingNumber, bookingId, next,
                        origin, destination, arrivalDeadline))
                .orElse(this);
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

    public TrackingStatus trackingStatus() {
        return trackingStatus;
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
