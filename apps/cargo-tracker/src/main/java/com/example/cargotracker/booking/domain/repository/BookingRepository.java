package com.example.cargotracker.booking.domain.repository;

import com.example.cargotracker.booking.domain.model.Booking;
import com.example.cargotracker.booking.domain.model.BookingId;

import java.util.Optional;

/**
 * 予約リポジトリのポートインターフェース（ドメイン層）。
 * アダプター実装は infrastructure/persistence 層に配置する。
 */
public interface BookingRepository {

    void save(Booking booking);

    Optional<Booking> findById(BookingId id);
}
