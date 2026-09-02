package com.example.bookingms.application.internal.commandservices;

import com.example.bookingms.domain.repository.CargoRepository;
import com.example.bookingms.domain.repository.CargoSummary;
import com.example.bookingms.domain.model.aggregates.Cargo;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 荷主の合意を得て予約を確定する（US13-2）。
 *
 * <p>通知していない予約は確定できない（[ADR-021] 決定 1）。その判定は集約が持つ。
 */
@Service
public class ConfirmBookingUseCase {

    private final CargoRepository cargoes;

    public ConfirmBookingUseCase(CargoRepository cargoes) {
        this.cargoes = cargoes;
    }

    /** 確定する。予約が見つからなければ空を返す。 */
    public Optional<Cargo> confirm(String bookingId) {
        return cargoes.findByBookingId(bookingId)
                .map(CargoSummary::cargo)
                .map(cargo -> cargoes.save(cargo.confirm()));
    }
}
