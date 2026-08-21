package com.example.bookingms.application.internal;

import com.example.bookingms.application.port.CargoRepository;
import com.example.bookingms.application.port.CargoSummary;
import com.example.bookingms.domain.model.Cargo;
import java.util.Optional;

/**
 * 条件では経路が組めないことを営業へ差し戻す（US10・[ADR-020] 決定 7）。
 *
 * <p>通知の仕組みが無いため、US06（経路設計の依頼）と同じ形で代替する。状態を持たせ、
 * 営業の一覧で気づけるようにする。
 */
public class RequestConsultationUseCase {

    private final CargoRepository cargoes;

    public RequestConsultationUseCase(CargoRepository cargoes) {
        this.cargoes = cargoes;
    }

    public Optional<Cargo> request(String bookingId) {
        return cargoes.findByBookingId(bookingId)
                .map(CargoSummary::cargo)
                .map(cargo -> cargoes.save(cargo.requestConsultation()));
    }
}
