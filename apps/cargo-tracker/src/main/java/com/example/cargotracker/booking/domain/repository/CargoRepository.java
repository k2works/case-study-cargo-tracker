package com.example.cargotracker.booking.domain.repository;

import com.example.cargotracker.booking.domain.model.BookingId;
import com.example.cargotracker.booking.domain.model.Cargo;
import java.util.Optional;

/** 貨物予約の出力ポート。実装はインフラ層に置く（DIP）。 */
public interface CargoRepository {

    /** 新規登録する。 */
    void save(Cargo cargo);

    /**
     * 更新する。
     *
     * <p>楽観的ロックにより、読み取り時から version が変わっていれば更新しない。
     *
     * @return 更新できたなら {@code true}。他の更新が先行していたなら {@code false}
     */
    boolean update(Cargo cargo);

    Optional<Cargo> findById(BookingId bookingId);
}
