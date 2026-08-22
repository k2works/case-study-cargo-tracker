package com.example.bookingms.domain.model;

import com.example.shared.domain.model.Location;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;

/**
 * 輸送の要件。どこからどこへ、いつまでに運ぶか。
 *
 * <p>到着期限は**目的地の暦**で判断する。UTC で判断すると、時差の分だけ受付が拒否される
 * 時間帯ができる。日中しか動かさないと気づかない種類の欠陥になる（ADR-010）。
 */
public final class RouteSpecification {

    private final Location origin;
    private final Location destination;
    private final LocalDate departureDate;
    private final LocalDate arrivalDeadline;

    private RouteSpecification(Location origin, Location destination, LocalDate departureDate,
            LocalDate arrivalDeadline) {
        this.origin = origin;
        this.destination = destination;
        this.departureDate = departureDate;
        this.arrivalDeadline = arrivalDeadline;
    }

    /**
     * 新しい要件を受け入れる。
     *
     * @param destinationZone 目的地の業務タイムゾーン。到着期限の「今日」を決めるのに使う
     * @param clock 現在時刻。テストで日付境界をまたぐため、固定値ではなく Clock で受け取る
     */
    public static RouteSpecification of(Location origin, Location destination,
            LocalDate departureDate, LocalDate arrivalDeadline, ZoneId destinationZone, Clock clock) {
        if (origin == null || destination == null) {
            throw new IllegalArgumentException("出発地と目的地は必須です");
        }
        if (origin.equals(destination)) {
            throw new IllegalArgumentException("出発地と目的地は同じにできません: " + origin.unLocode());
        }
        if (arrivalDeadline == null) {
            throw new IllegalArgumentException("到着期限は必須です");
        }

        // 期限「当日」はまだ間に合う。日付単位で比べる（時刻付きの到着と素朴に比較すると
        // 期限当日に着く便を誤って刈る）
        LocalDate todayAtDestination = LocalDate.now(clock.withZone(destinationZone));
        if (arrivalDeadline.isBefore(todayAtDestination)) {
            throw new IllegalArgumentException("到着期限に過去の日付は指定できません: " + arrivalDeadline);
        }
        if (departureDate != null && departureDate.isAfter(arrivalDeadline)) {
            throw new IllegalArgumentException("希望出発日が到着期限より後になっています");
        }
        return new RouteSpecification(origin, destination, departureDate, arrivalDeadline);
    }

    /**
     * 日程だけを差し替える（US06 の訂正・IT6 タスク 0.11）。
     *
     * <p><strong>出発地と目的地は変えない。</strong>変えるならそれは別の予約であり、経路も
     * 荷役の段取りも一から組み直しになる。ここで許すのは日程の訂正だけである。
     *
     * <p>期限が過去でないことは<strong>ここでは見ない</strong>。呼び出し側（集約）が
     * 受け入れる状態を絞っており、期限の妥当性は {@link #of} と同じ規則で確かめたい。
     * したがって {@link #of} を通す。
     */
    public RouteSpecification withSchedule(LocalDate newDepartureDate,
            LocalDate newArrivalDeadline, ZoneId destinationZone, Clock clock) {
        return of(origin, destination, newDepartureDate, newArrivalDeadline, destinationZone,
                clock);
    }

    /** 永続化された行から復元する。ここでは検査しない。 */
    public static RouteSpecification restore(Location origin, Location destination,
            LocalDate departureDate, LocalDate arrivalDeadline) {
        return new RouteSpecification(origin, destination, departureDate, arrivalDeadline);
    }

    /**
     * その旅程はこの要件を満たすか（US09）。
     *
     * <p>見るのは 2 つ。<strong>端点が一致すること</strong>と、<strong>期限内に着くこと</strong>。
     * 経由地は問わない（どこを通るかは経路設計者の判断であり、荷主の要件ではない）。
     *
     * <p><strong>期限は日付である。</strong>「9 月 30 日まで」は「30 日中に着けばよい」を意味する。
     * 時刻付きの到着と素朴に比較すると期限当日に着く便を誤って刈る。しかも目的地の暦で
     * 判断しないと、時差の分だけ当日が短くなる（[ADR-010]・[ADR-017] 決定 3 と同じ規則）。
     *
     * @param destinationZone 目的地の業務タイムゾーン。到着期限の「当日」を決めるのに使う
     */
    public boolean isSatisfiedBy(CargoItinerary itinerary, ZoneId destinationZone) {
        if (itinerary == null) {
            return false;
        }
        LocalDate arrivalDateAtDestination =
                LocalDate.ofInstant(itinerary.expectedArrivalTime(), destinationZone);
        return origin.equals(itinerary.origin())
                && destination.equals(itinerary.destination())
                && !arrivalDateAtDestination.isAfter(arrivalDeadline);
    }

    public Location origin() {
        return origin;
    }

    public Location destination() {
        return destination;
    }

    /** 希望出発日。荷主が指定しないこともある。 */
    public Optional<LocalDate> departureDate() {
        return Optional.ofNullable(departureDate);
    }

    public LocalDate arrivalDeadline() {
        return arrivalDeadline;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RouteSpecification spec
                && origin.equals(spec.origin)
                && destination.equals(spec.destination)
                && Objects.equals(departureDate, spec.departureDate)
                && arrivalDeadline.equals(spec.arrivalDeadline);
    }

    @Override
    public int hashCode() {
        return Objects.hash(origin, destination, departureDate, arrivalDeadline);
    }

    @Override
    public String toString() {
        return "%s → %s（%s まで）".formatted(origin.unLocode(), destination.unLocode(), arrivalDeadline);
    }
}
