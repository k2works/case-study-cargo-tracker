package com.example.bookingms.application.internal;

import com.example.bookingms.application.port.CargoRepository;
import com.example.bookingms.application.port.CargoSummary;
import com.example.bookingms.domain.model.Cargo;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 荷主が変更を希望したので経路設計へ戻す（US13-4・[ADR-021] 決定 3・決定 4）。
 *
 * <p>戻す先と、戻せる範囲の判定は集約が持つ。
 */
@Service
public class ReturnToRoutingUseCase {

    private final CargoRepository cargoes;

    public ReturnToRoutingUseCase(CargoRepository cargoes) {
        this.cargoes = cargoes;
    }

    /** 戻す。予約が見つからなければ空を返す。 */
    public Optional<Cargo> returnToRouting(String bookingId) {
        return cargoes.findByBookingId(bookingId)
                .map(CargoSummary::cargo)
                .map(cargo -> cargoes.save(cargo.returnToRouting()));
    }
}
