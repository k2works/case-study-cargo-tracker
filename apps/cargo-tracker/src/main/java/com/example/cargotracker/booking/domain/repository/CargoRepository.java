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

    /**
     * 追跡番号の発行を保存する（US14）。
     *
     * <p>予約状態と追跡番号を<strong>1 つの操作として書く</strong>。
     *
     * @return 他の更新が先行していれば {@code false}（楽観的ロック）
     */
    boolean updateTrackingNumber(Cargo cargo);

    Optional<Cargo> findById(BookingId bookingId);

    /**
     * 追跡番号から引き当てる（US15 / US18）。
     *
     * <p>荷役作業員が手に持っているのは追跡番号だけである。
     */
    Optional<Cargo> findByTrackingNumber(String trackingNumber);
}
