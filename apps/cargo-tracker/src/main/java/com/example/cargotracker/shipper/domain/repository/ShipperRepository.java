package com.example.cargotracker.shipper.domain.repository;

import com.example.cargotracker.shared.domain.model.valueobjects.ShipperId;
import com.example.cargotracker.shipper.domain.model.valueobjects.Email;
import com.example.cargotracker.shipper.domain.model.aggregates.Shipper;
import java.util.List;
import java.util.Optional;
import javax.annotation.CheckReturnValue;

/** 荷主の出力ポート。実装はインフラ層に置く（DIP）。 */
public interface ShipperRepository {

    void save(Shipper shipper);

    Optional<Shipper> findById(ShipperId id);

    Optional<Shipper> findByEmail(String email);

    Optional<Shipper> findByShipperCode(String shipperCode);

    List<Shipper> findAll();

    /**
     * 訂正する（US32）。
     *
     * <p>楽観的ロックにより、読み取り時から version が変わっていれば更新しない。
     *
     * @return 訂正できたなら {@code true}。他の訂正が先行していたなら {@code false}
     */
    @CheckReturnValue
    boolean update(Shipper shipper);

    /** 荷主コードの採番に使う次の連番を取得する。 */
    long nextSequence();

    default Optional<Shipper> findByEmail(Email email) {
        return findByEmail(email.value());
    }
}
