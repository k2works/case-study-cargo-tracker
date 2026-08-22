package com.example.bookingms.application.internal;

import com.example.bookingms.application.port.CargoEventNotifier;
import com.example.bookingms.application.port.CargoRepository;
import com.example.bookingms.application.port.CargoSummary;
import com.example.bookingms.application.port.TrackingNumberIssued;
import com.example.bookingms.domain.model.Cargo;
import com.example.bookingms.domain.model.TrackingNumber;
import java.time.Clock;
import java.util.Optional;

/**
 * 追跡番号を発行する（US14・[ADR-021]・[ADR-022]）。
 *
 * <p><strong>採番は永続化の経路が行う</strong>（[ADR-011] と同じ形）。ここで文字列を作ると、
 * 別の入口が違う形式を発行できてしまい、サービスをまたいだ照合が壊れる。
 *
 * <p>発行できたら他のサービスへ伝える（[ADR-022] 決定 1）。<strong>伝えるのは出力ポート越し</strong>
 * であり、ここは AMQP を知らない。実際に流れるのはコミットのあとである（決定 6）。
 */
public class IssueTrackingNumberUseCase {

    private final CargoRepository cargoes;
    private final CargoEventNotifier events;
    private final Clock clock;

    public IssueTrackingNumberUseCase(CargoRepository cargoes, CargoEventNotifier events,
            Clock clock) {
        this.cargoes = cargoes;
        this.events = events;
        this.clock = clock;
    }

    /** 発行する。予約が見つからなければ空を返す。 */
    public Optional<Cargo> issue(String bookingId) {
        return cargoes.findByBookingId(bookingId)
                .map(CargoSummary::cargo)
                .map(cargo -> {
                    TrackingNumber number = TrackingNumber.of(cargoes.nextTrackingNumber());
                    Cargo issued = cargoes.save(cargo.issueTrackingNumber(number));
                    events.trackingNumberIssued(eventOf(issued, number));
                    return issued;
                });
    }

    private TrackingNumberIssued eventOf(Cargo cargo, TrackingNumber number) {
        var route = cargo.routeSpecification();
        return new TrackingNumberIssued(
                number.value(),
                cargo.bookingId().orElseThrow().value(),
                route.origin().unLocode(),
                route.destination().unLocode(),
                route.arrivalDeadline(),
                clock.instant());
    }
}
