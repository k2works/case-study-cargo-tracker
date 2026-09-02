package com.example.bookingms.application.internal.commandservices;

import com.example.bookingms.domain.repository.CargoRepository;
import com.example.bookingms.domain.repository.CargoSummary;
import com.example.bookingms.domain.repository.LocationRepository;
import com.example.bookingms.domain.model.aggregates.Cargo;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 予約の日程を訂正する（US06 の訂正・IT6 タスク 0.11）。
 *
 * <p>条件協議の結果が「期限を延ばす」だったとき、予約を直せないと再依頼しても同じ結果になる。
 * 直せる範囲と可否の判定は集約が持つ。
 */
@Service
public class ReviseBookingScheduleUseCase {

    private final CargoRepository cargoes;
    private final LocationRepository locations;
    private final Clock clock;

    public ReviseBookingScheduleUseCase(CargoRepository cargoes, LocationRepository locations,
            Clock clock) {
        this.cargoes = cargoes;
        this.locations = locations;
        this.clock = clock;
    }

    /** 訂正する。予約が見つからなければ空を返す。 */
    public Optional<Cargo> revise(String bookingId, LocalDate departureDate,
            LocalDate arrivalDeadline) {
        return cargoes.findByBookingId(bookingId)
                .map(CargoSummary::cargo)
                .map(cargo -> cargoes.save(cargo.reviseSchedule(departureDate, arrivalDeadline,
                        destinationZoneOf(cargo), clock)));
    }

    /**
     * 到着期限の「今日」は目的地の暦で決まる（[ADR-010]）。
     *
     * <p>マスタに無いのは<strong>こちら側の不備</strong>であり、利用者に作業を促しても直らない。
     */
    private ZoneId destinationZoneOf(Cargo cargo) {
        return locations.timeZoneOf(cargo.routeSpecification().destination().unLocode())
                .orElseThrow(() -> new LocationMasterMissingException(
                        cargo.routeSpecification().destination().unLocode()));
    }
}
