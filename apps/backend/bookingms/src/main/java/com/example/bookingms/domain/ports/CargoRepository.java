package com.example.bookingms.domain.ports;

import com.example.bookingms.domain.model.aggregates.Cargo;
import com.example.bookingms.domain.model.valueobjects.BookingId;

import java.util.List;
import java.util.Optional;

/**
 * 貨物リポジトリポート（ドメイン層インターフェース）
 */
public interface CargoRepository {

    /**
     * 貨物を保存する（新規作成）
     *
     * @param cargo 保存する貨物
     * @return id が付与された貨物
     */
    Cargo save(Cargo cargo);

    /**
     * 予約 ID で貨物を検索する
     *
     * @param bookingId 予約 ID
     * @return 貨物（存在しない場合は空）
     */
    Optional<Cargo> findByBookingId(BookingId bookingId);

    /**
     * すべての貨物を取得する
     *
     * @return 貨物リスト
     */
    List<Cargo> findAll();
}
