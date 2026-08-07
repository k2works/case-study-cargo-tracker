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

    /**
     * 経路の割り当てを保存する（US09 / US11）。
     *
     * <p>経路状態と旅程を<strong>1 つの操作として書く</strong>。旅程は丸ごと
     * 入れ替える。
     *
     * @return 他の更新が先行していれば {@code false}（楽観的ロック）
     */
    boolean updateRouting(Cargo cargo);

    Optional<Cargo> findById(BookingId bookingId);
}
