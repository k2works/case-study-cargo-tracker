package com.example.cargotracker.shipper.domain.repository;

import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.shipper.domain.model.Email;
import com.example.cargotracker.shipper.domain.model.Shipper;
import java.util.List;
import java.util.Optional;

/** 荷主の出力ポート。実装はインフラ層に置く（DIP）。 */
public interface ShipperRepository {

    void save(Shipper shipper);

    Optional<Shipper> findById(ShipperId id);

    Optional<Shipper> findByEmail(String email);

    List<Shipper> findAll();

    /** 荷主コードの採番に使う次の連番を取得する。 */
    long nextSequence();

    default Optional<Shipper> findByEmail(Email email) {
        return findByEmail(email.value());
    }
}
