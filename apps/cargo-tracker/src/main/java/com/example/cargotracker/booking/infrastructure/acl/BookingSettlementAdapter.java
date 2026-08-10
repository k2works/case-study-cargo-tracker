package com.example.cargotracker.booking.infrastructure.acl;

import com.example.cargotracker.billing.application.internal.outboundservices.acl
        .BookingSettlementPort;
import com.example.cargotracker.booking.domain.model.BookingId;
import com.example.cargotracker.booking.domain.model.Cargo;
import com.example.cargotracker.booking.domain.repository.CargoRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link BookingSettlementPort} の実装（ACL のアダプタ。US23）。
 *
 * <p><strong>遷移してよいかは予約が決める</strong>（遷移表 #8）。ここで
 * {@code booking_status} を直接 UPDATE すると、終端状態の意味が
 * <strong>SQL を書いた人ごとに変わる</strong>。
 *
 * <p><strong>できなかったことを例外にしない。</strong> 入金の記録は済んでいるため、
 * 例外を投げると「入金は記録したのに画面は 500」になる。
 */
@Component
public class BookingSettlementAdapter implements BookingSettlementPort {

    private final CargoRepository cargoRepository;

    public BookingSettlementAdapter(CargoRepository cargoRepository) {
        this.cargoRepository = cargoRepository;
    }

    @Override
    public boolean settle(String bookingId) {
        if (bookingId == null || bookingId.isBlank()) {
            return false;
        }
        UUID id;
        try {
            id = UUID.fromString(bookingId.strip());
        } catch (IllegalArgumentException e) {
            return false;
        }
        Cargo cargo = cargoRepository.findById(new BookingId(id)).orElse(null);
        // **述語で確かめてから進める。** すでに精算済みの予約に再び入金確認が
        // 走ることはありうる（画面の二重送信）。それで例外にしない
        if (cargo == null || !cargo.canSettle()) {
            return false;
        }
        cargo.settle();
        return cargoRepository.update(cargo);
    }
}
