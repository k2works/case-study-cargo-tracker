package com.example.cargotracker.billing.domain.model.repository;

import com.example.cargotracker.billing.domain.model.aggregates.FreightCharge;
import com.example.cargotracker.billing.domain.model.aggregates.FreightId;

import java.util.List;
import java.util.Optional;

/**
 * 輸送料金リポジトリインターフェース。
 */
public interface FreightChargeRepository {

    void save(FreightCharge charge);

    Optional<FreightCharge> findById(FreightId id);

    List<FreightCharge> findByBookingId(String bookingId);

    List<FreightCharge> findAll();
}
