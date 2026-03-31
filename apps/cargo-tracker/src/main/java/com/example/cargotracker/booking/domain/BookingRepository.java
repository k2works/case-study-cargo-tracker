package com.example.cargotracker.booking.domain;

import java.util.Optional;

/**
 * 予約リポジトリのポートインターフェース（ドメイン層）。
 * アダプター実装は infrastructure/persistence 層に配置する。
 */
public interface BookingRepository {

    void save(Booking booking);

    Optional<Booking> findById(BookingId id);
}
