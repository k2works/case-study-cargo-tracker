package com.example.cargotracker.shipper.domain.repository;

import com.example.cargotracker.shipper.domain.model.aggregates.Shipper;
import com.example.cargotracker.shared.domain.model.ShipperId;

import java.util.Optional;

/**
 * 荷主リポジトリのポートインターフェース（ドメイン層）。
 * アダプター実装は infrastructure/repositories 層に配置する。
 */
public interface ShipperRepository {

    void save(Shipper shipper);

    Optional<Shipper> findById(ShipperId id);

    Optional<Shipper> findByEmail(String email);
}
