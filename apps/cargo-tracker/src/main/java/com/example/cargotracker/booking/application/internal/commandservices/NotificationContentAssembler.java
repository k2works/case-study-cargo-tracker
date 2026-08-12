package com.example.cargotracker.booking.application.internal.commandservices;

import com.example.cargotracker.booking.application.internal.outboundservices.acl.RouteRelaxations;
import com.example.cargotracker.booking.application.internal.queryservices.BookingView;
import com.example.cargotracker.booking.domain.model.NotificationContent;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 通知に載せる内容を組み立てる（US12）。
 *
 * <p><strong>料金は載せない。</strong> 概算（ADR-008）は候補の並べ替え用であり、
 * 荷主に見せた瞬間に請求額として読まれる。料金は US21（Release 2.0）。
 *
 * <p><strong>期限を延ばして割り当てた場合は差分を載せる</strong>
 * （{@code ui_design.md} 経路割り当て §候補ゼロ時の再算出）。
 * 荷主が知る必要があるのは「いつ着くか」だけではなく、
 * <strong>当初の約束から何日ずれたか</strong>である。
 */
@Component
public class NotificationContentAssembler {

    private final RouteRelaxations routeRelaxations;
    private final Clock clock;

    public NotificationContentAssembler(RouteRelaxations routeRelaxations, Clock clock) {
        this.routeRelaxations = routeRelaxations;
        this.clock = clock;
    }

    /**
     * 予約の確定経路から通知内容を組み立てる。
     *
     * @throws IllegalArgumentException 送るべき中身が無い場合（経路が確定していない）
     */
    public NotificationContent assemble(BookingView booking) {
        List<BookingView.ItineraryLegView> legs = booking.delivery().itinerary();
        if (legs.isEmpty()) {
            throw new IllegalArgumentException("経路が確定していないため通知できません");
        }

        List<String> voyageNumbers = legs.stream()
                .map(BookingView.ItineraryLegView::voyageNumber)
                .distinct()
                .toList();

        // 経由港は「最後の区間以外の荷降港」である。直行なら空になる
        List<String> transitPorts = new ArrayList<>();
        for (int i = 0; i < legs.size() - 1; i++) {
            transitPorts.add(legs.get(i).unloadLocation());
        }

        var departure = legs.getFirst().loadTime();
        var arrival = legs.getLast().unloadTime();
        long transitDays = ChronoUnit.DAYS.between(
                departure.atZone(clock.getZone()).toLocalDate(),
                arrival.atZone(clock.getZone()).toLocalDate());

        var relaxation = routeRelaxations.find(booking.bookingId());

        return new NotificationContent(
                new NotificationContent.Itinerary(
                        transitPorts,
                        transitDays,
                        arrival.atZone(clock.getZone()).toLocalDate(),
                        voyageNumbers),
                booking.tracking().hasNumber() ? booking.tracking().number() : null,
                new NotificationContent.Deadline(
                        relaxation.map(RouteRelaxations.Relaxation::originalDeadline)
                                .orElse(null),
                        relaxation.map(RouteRelaxations.Relaxation::extraDays).orElse(0L),
                        // **予約の期限と比べる**（US28）。延ばした期限に「間に合っている」
                        // ことは荷主の関心ではない。知りたいのは当初の約束から
                        // 何日ずれたかである。
                        // 日付単位で比べる（期限は日付、到着は時刻を持つ）
                        Math.max(0L, ChronoUnit.DAYS.between(
                                booking.delivery().arrivalDeadline(),
                                arrival.atZone(clock.getZone()).toLocalDate()))));
    }
}
